/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package web.view.ukhorskaya.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.reference.SoftReference;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MyCodeCompletionHandlerBase implements CodeInsightActionHandler {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
    private final CompletionType myCompletionType;
    final boolean invokedExplicitly;
    final boolean autopopup;

    public String getResultString() {
        return resultString.toString();
    }

    private StringBuilder resultString = new StringBuilder();

    public MyCodeCompletionHandlerBase(final CompletionType completionType) {
        this(completionType, true, false);
    }

    public MyCodeCompletionHandlerBase(CompletionType completionType, boolean invokedExplicitly, boolean autopopup) {
        myCompletionType = completionType;
        this.invokedExplicitly = invokedExplicitly;
        this.autopopup = autopopup;
    }


    public final void invokeCompletion(final Project project, final Editor editor) {
        try {
            invokeCompletion(project, editor, 1, false);
        } catch (IndexNotReadyException e) {
            System.err.println("Code completion is not available here while indices are being built");
        }
    }

    public final void invokeCompletion(final Project project, final Editor editor, int time, boolean hasModifiers) {
        final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        assert psiFile != null : "no PSI file: " + FileDocumentManager.getInstance().getFile(editor.getDocument());

        final Document document = editor.getDocument();


        psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);

        CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
        boolean repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(myCompletionType, editor);
        if (repeated && isAutocompleteCommonPrefixOnInvocation() && phase.fillInCommonPrefix()) {
            return;
        }

        time = phase.newCompletionStarted(time, repeated);
        CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.getClass());

        if (time > 1) {
            if (myCompletionType == CompletionType.CLASS_NAME) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION);
            } else if (myCompletionType == CompletionType.BASIC) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION);
            }
        }

        final CompletionInitializationContext[] initializationContext = {null};


        Runnable initCmd = new Runnable() {
            @Override
            public void run() {

                Runnable runnable = new Runnable() {
                    public void run() {
                        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

                        EditorUtil.fillVirtualSpaceUntilCaret(editor);
                        documentManager.commitAllDocuments();

                        if (editor.getDocument().getTextLength() != psiFile.getTextLength()) {
                            if (ApplicationManagerEx.getApplicationEx().isInternal()) {
                                throw new AssertionError("unsuccessful commit: docText=" + editor.getDocument().getText() + "; fileText=" + psiFile.getText() + "; injected=" + (editor instanceof EditorWindow));
                            }

                            throw new AssertionError("unsuccessful commit: injected=" + (editor instanceof EditorWindow));
                        }

                        final Ref<CompletionContributor> current = Ref.create(null);
                        initializationContext[0] = new CompletionInitializationContext(editor, psiFile, myCompletionType) {
                            CompletionContributor dummyIdentifierChanger;

                            @Override
                            public void setFileCopyPatcher(@NotNull FileCopyPatcher fileCopyPatcher) {
                                super.setFileCopyPatcher(fileCopyPatcher);

                                if (dummyIdentifierChanger != null) {
                                    LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
                                }
                                dummyIdentifierChanger = current.get();
                            }
                        };
                        for (final CompletionContributor contributor : CompletionContributor.forLanguage(initializationContext[0].getPositionLanguage())) {
                            if (DumbService.getInstance(project).isDumb() && !DumbService.isDumbAware(contributor)) {
                                continue;
                            }

                            current.set(contributor);
                            contributor.beforeCompletion(initializationContext[0]);
                            assert !documentManager.isUncommited(document) : "Contributor " + contributor + " left the document uncommitted";
                        }
                    }
                };
                ApplicationManager.getApplication().runWriteAction(runnable);
            }
        };

        CommandProcessor.getInstance().executeCommand(project, initCmd, null, null);

        doComplete(time, initializationContext[0], hasModifiers);
    }

    @NotNull
    private LookupImpl obtainLookup(Editor editor) {
        LookupImpl existing = (LookupImpl) LookupManager.getActiveLookup(editor);
        if (existing != null && existing.isCompletion()) {
            existing.markReused();
            if (!autopopup) {
                existing.setFocused(true);
            }
            return existing;
        }

        LookupImpl lookup = (LookupImpl) LookupManager.getInstance(editor.getProject()).createLookup(editor, LookupElement.EMPTY_ARRAY, "", LookupArranger.DEFAULT);
        if (editor.isOneLineMode()) {
            lookup.setCancelOnClickOutside(true);
            lookup.setCancelOnOtherWindowOpen(true);
            lookup.setResizable(false);
            lookup.setForceLightweightPopup(false);
        }
        lookup.setFocused(!autopopup);
        return lookup;
    }

    private void doComplete(final int invocationCount, CompletionInitializationContext initContext, boolean hasModifiers) {
        final Editor editor = initContext.getEditor();

        final CompletionParameters parameters = createCompletionParameters(invocationCount, initContext);

        final Semaphore freezeSemaphore = new Semaphore();
        freezeSemaphore.down();
        //WARN NULL FOR HANDLER
        final CompletionProgressIndicator indicator = new CompletionProgressIndicator(editor, parameters, null, freezeSemaphore,
                initContext.getOffsetMap(), hasModifiers);

        boolean sync =
                (invokedExplicitly || ApplicationManager.getApplication().isUnitTestMode()) && !CompletionAutoPopupHandler.ourTestingAutopopup;

        CompletionServiceImpl.setCompletionPhase(sync ? new CompletionPhase.Synchronous(indicator) : new CompletionPhase.BgCalculation(indicator));

        final AtomicReference<LookupElement[]> data = startCompletionThread(parameters, indicator, initContext);

        if (!sync) {
            return;
        }

        if (freezeSemaphore.waitFor(2000)) {
            final LookupElement[] allItems = data.get();
            if (allItems != null) { // the completion is really finished, now we may auto-insert or show lookup
                completionFinished(allItems);
            }
        }

        //     CompletionServiceImpl.setCompletionPhase(new CompletionPhase.BgCalculation(indicator));
        //      indicator.showLookup();
    }

    private static AtomicReference<LookupElement[]> startCompletionThread(final CompletionParameters parameters,
                                                                          final CompletionProgressIndicator indicator,
                                                                          final CompletionInitializationContext initContext) {


        final AtomicReference<LookupElement[]> data = new AtomicReference<LookupElement[]>(null);
        final Runnable computeRunnable = new Runnable() {
            public void run() {
                ProgressManager.getInstance().runProcess(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ApplicationManager.getApplication().runReadAction(new Runnable() {
                                public void run() {
                                    data.set(CompletionService.getCompletionService().performCompletion(parameters, new Consumer<CompletionResult>() {
                                        public void consume(final CompletionResult result) {
                                            System.out.println("compute = " + result.getLookupElement().getLookupString());
                                            // indicator.addItem(result);
                                        }
                                    }));
                                }
                            });
                        } catch (ProcessCanceledException ignored) {
                        }
                    }
                }, indicator);
            }
        };

        if (ApplicationManager.getApplication().isUnitTestMode() && !CompletionAutoPopupHandler.ourTestingAutopopup) {
            computeRunnable.run();
        } else {
            ApplicationManager.getApplication().executeOnPooledThread(computeRunnable);
        }

        return data;
    }


    private CompletionParameters createCompletionParameters(int invocationCount, final CompletionInitializationContext initContext) {
        final Ref<CompletionContext> ref = Ref.create(null);
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        ref.set(insertDummyIdentifier(initContext));
                    }
                });
            }
        });
        final CompletionContext newContext = ref.get();

        final int offset = newContext.getStartOffset();
        final PsiFile fileCopy = newContext.file;
        final PsiElement insertedElement = newContext.file.findElementAt(newContext.getStartOffset());
        if (insertedElement == null) {
            throw new AssertionError("offset " + newContext.getStartOffset() + " at:\n text=\"" + fileCopy.getText() + "\"\n instance=" + fileCopy);
        }
        insertedElement.putUserData(CompletionContext.COMPLETION_CONTEXT_KEY, newContext);

        LOG.assertTrue(fileCopy.findElementAt(offset) == insertedElement, "wrong offset");

        final TextRange range = insertedElement.getTextRange();
        if (!range.substring(fileCopy.getText()).equals(insertedElement.getText())) {
            LOG.error("wrong text: copy='" + fileCopy.getText() + "'; element='" + insertedElement.getText() + "'; range=" + range);
        }

        return new MyCompletionParameters(insertedElement, fileCopy.getOriginalFile(), myCompletionType, offset, invocationCount, obtainLookup(initContext.getEditor()), false);
    }


    protected void completionFinished(final LookupElement[] items) {
        if (items.length != 0) {
            System.out.println("FINISHED");
            for (int i = 0; i < items.length; ++i) {
                if (i != 0) {
                    LookupElementPresentation presentation = new LookupElementPresentation();
                    items[i].renderElement(presentation);
                    System.out.println(presentation.getTailText());
                    resultString.append(" ");
                }
                resultString.append(items[i].getLookupString());
            }
        }

    }

    private CompletionContext insertDummyIdentifier(CompletionInitializationContext initContext) {
        final PsiFile originalFile = initContext.getFile();
        PsiFile fileCopy = createFileCopy(originalFile);
        PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(fileCopy);
        final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(hostFile.getProject());
        int hostStartOffset = injectedLanguageManager.injectedToHost(fileCopy, initContext.getStartOffset());
        final Editor hostEditor = InjectedLanguageUtil.getTopLevelEditor(initContext.getEditor());

        final OffsetMap hostMap = new OffsetMap(hostEditor.getDocument());
        final OffsetMap original = initContext.getOffsetMap();
        for (final OffsetKey key : new ArrayList<OffsetKey>(original.keySet())) {
            hostMap.addOffset(key, injectedLanguageManager.injectedToHost(fileCopy, original.getOffset(key)));
        }

        Document document = fileCopy.getViewProvider().getDocument();
        assert document != null : "no document";
        initContext.getFileCopyPatcher().patchFileCopy(fileCopy, document, initContext.getOffsetMap());
        final Document hostDocument = hostFile.getViewProvider().getDocument();
        assert hostDocument != null : "no host document";
        PsiDocumentManager.getInstance(hostFile.getProject()).commitDocument(hostDocument);
        assert hostFile.isValid() : "file became invalid";
        assert hostMap.getOffset(CompletionInitializationContext.START_OFFSET) < hostFile.getTextLength() : "startOffset outside the host file";

        CompletionContext context;
        PsiFile injected = InjectedLanguageUtil.findInjectedPsiNoCommit(hostFile, hostStartOffset);
        if (injected != null) {
            assert hostStartOffset >= injectedLanguageManager.injectedToHost(injected, 0) : "startOffset before injected";
            assert hostStartOffset <= injectedLanguageManager.injectedToHost(injected, injected.getTextLength()) : "startOffset after injected";

            EditorWindow injectedEditor = (EditorWindow) InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(hostEditor, hostFile, hostStartOffset);
            assert injected == injectedEditor.getInjectedFile();
            final OffsetMap map = new OffsetMap(injectedEditor.getDocument());
            for (final OffsetKey key : new ArrayList<OffsetKey>(hostMap.keySet())) {
                map.addOffset(key, injectedEditor.logicalPositionToOffset(injectedEditor.hostToInjected(hostEditor.offsetToLogicalPosition(hostMap.getOffset(key)))));
            }
            context = new CompletionContext(initContext.getProject(), injectedEditor, injected, map);
            assert hostStartOffset == injectedLanguageManager.injectedToHost(injected, context.getStartOffset()) : "inconsistent injected offset translation";
        } else {
            context = new CompletionContext(initContext.getProject(), hostEditor, hostFile, hostMap);
        }

        assert context.getStartOffset() < context.file.getTextLength() : "start outside the file";
        assert context.getStartOffset() >= 0 : "start < 0";

        return context;
    }

    private boolean isAutocompleteCommonPrefixOnInvocation() {
        return invokedExplicitly && CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
    }


    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        invokeCompletion(project, editor);
    }

    public boolean startInWriteAction() {
        return false;
    }

    public static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

    protected PsiFile createFileCopy(PsiFile file) {
        ((PsiModificationTrackerImpl) file.getManager().getModificationTracker()).incCounter();
        final VirtualFile virtualFile = file.getVirtualFile();
        if (file.isPhysical() && virtualFile != null && virtualFile.getFileSystem() == LocalFileSystem.getInstance()
                // must not cache injected file copy, since it does not reflect changes in host document
                && !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
            final SoftReference<Pair<PsiFile, Document>> reference = file.getUserData(FILE_COPY_KEY);
            if (reference != null) {
                final Pair<PsiFile, Document> pair = reference.get();
                if (pair != null && pair.first.isValid() && pair.first.getClass().equals(file.getClass())) {
                    final PsiFile copy = pair.first;
                    final Document document = pair.second;
                    assert document != null;
                    final String oldDocumentText = document.getText();
                    final String oldCopyText = copy.getText();
                    final String newText = file.getText();
                    document.setText(newText);
                    try {
                        PsiDocumentManager.getInstance(copy.getProject()).commitDocument(document);
                        return copy;
                    } catch (Throwable e) {
                        document.setText("");
                        if (((ApplicationEx) ApplicationManager.getApplication()).isInternal()) {
                            final StringBuilder sb = new StringBuilder();
                            boolean oldsAreSame = Comparing.equal(oldCopyText, oldDocumentText);
                            if (oldsAreSame) {
                                sb.append("oldCopyText == oldDocumentText");
                            } else {
                                sb.append("oldCopyText != oldDocumentText");
                                sb.append("\n--- oldCopyText ------------------------------------------------\n").append(oldCopyText);
                                sb.append("\n--- oldDocumentText ------------------------------------------------\n").append(oldDocumentText);
                            }
                            if (Comparing.equal(oldCopyText, newText)) {
                                sb.insert(0, "newText == oldCopyText; ");
                            } else if (!oldsAreSame && Comparing.equal(oldDocumentText, newText)) {
                                sb.insert(0, "newText == oldDocumentText; ");
                            } else {
                                sb.insert(0, "newText != oldCopyText, oldDocumentText; ");
                                if (oldsAreSame) {
                                    sb.append("\n--- oldCopyText ------------------------------------------------\n").append(oldCopyText);
                                }
                                sb.append("\n--- newText ------------------------------------------------\n").append(newText);
                            }
                            LOG.error(sb.toString(), e);
                        }
                    }
                }
            }
        }

        final PsiFile copy = (PsiFile) file.copy();
        final Document document = copy.getViewProvider().getDocument();
        assert document != null;
        file.putUserData(FILE_COPY_KEY, new SoftReference<Pair<PsiFile, Document>>(Pair.create(copy, document)));
        return copy;
    }


}
