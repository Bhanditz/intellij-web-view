package web.view.ukhorskaya.autocomplete;

import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.DeferredIcon;
import com.intellij.ui.RowIcon;

import javax.swing.*;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/31/11
 * Time: 4:56 PM
 */
public class JsonResponse {

    private FilteringGotoByModel myModel;
    private boolean USE_NON_PROJECT_FILES = true;

    private final int COUNT_OF_ITEM_IN_LIST = 30;

    private StringBuilder response = new StringBuilder();
    private Project currentProject;

    public JsonResponse(FilteringGotoByModel model) {
        this.myModel = model;
    }

    public String getResponse(final Project currentProject, PsiFile psiFile, final String term) {
        this.currentProject = currentProject;

        final MyChooseByNameBase chooser = new MyChooseByNameBase(currentProject, myModel, psiFile, USE_NON_PROJECT_FILES);
        //final ArrayList<String> list = new ArrayList<String>();
        final HashSet<Object> set = new HashSet<Object>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                chooser.addElementsByPattern(set, term);
            }
        });

        final Object[] list = set.toArray();

        response.append("[");
        for (int i = 0; i < COUNT_OF_ITEM_IN_LIST && i < list.length; i++) {

            if (list[i].toString().equals("...")) {
                continue;
            }
            NavigationItem item = (NavigationItem) list[i];
            PsiElement element = (PsiElement) list[i];
            ItemPresentation presentation = item.getPresentation();

            final MyItemPresentation resultPresentation;
            if (element instanceof PsiBinaryFile) {
                resultPresentation = setResultPresentationForBinary((PsiBinaryFile) element);
            } else if (item.getPresentation() == null) {
                continue;
            } else {
                resultPresentation = setResultPresentation(element, presentation);
            }
            if ((i != 0) && (response.length() > 5)) {
                response.append(",");
            }
            response.append("{\"label\":\"");
            response.append(resultPresentation.name);
            response.append("\", \"icon\":\"");
            getJsonResponseForIcon(resultPresentation);

            String path = "";
            response.append("\", \"url\":\"");
            if (resultPresentation.url != null) {
                path = resultPresentation.url;
                response.append("/path=").append(path);
            }

            response.append("\", \"path\":\"");
            getJsonResponseForPath(item, resultPresentation, path);
            response.append("\", \"moduleName\":\"");
            response.append("<div style='float: right;'>");
            if (resultPresentation.module != null) {
                response.append(resultPresentation.module);
            }
            getJsonResponseForModuleIcon(resultPresentation);
            response.append("</div>");
            response.append("\"}");
        }

        if (response.length() < 5) {
            response = new StringBuilder();
            response.append("[");
            response.append("{\"label\":\"null\"}");
        }
        response.append("]");

        return response.toString();
    }

    private MyItemPresentation setResultPresentationForBinary(final PsiBinaryFile element) {
        final MyItemPresentation resultPresentation = new MyItemPresentation();
        resultPresentation.name = element.getName();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                resultPresentation.navigation = "";
                Icon icon = element.getIcon(0);
                if (icon instanceof DeferredIcon) {
                    resultPresentation.icon = ((DeferredIcon) icon).evaluate();
                } else {
                    resultPresentation.icon = icon;
                }

                VirtualFile file;
                file = element.getVirtualFile();
                resultPresentation.url = file.getUrl();

                Module module = ModuleUtil.findModuleForFile(file, currentProject);
                if (module != null) {
                    resultPresentation.module = module.getName();
                    //resultPresentation.moduleIcon = module.getModuleType().getNodeIcon(true);
                }
            }
        });

        return resultPresentation;
    }

    private void getJsonResponseForModuleIcon(MyItemPresentation resultPresentation) {
        if (resultPresentation.moduleIcon != null) {
            response.append("       ");
            response.append(getIconUrl(IconHelper.getInstance().addIconToMap(resultPresentation.moduleIcon)));
        }
    }

    private String getIconUrl(int hashCode) {
        StringBuilder response = new StringBuilder();
        response.append("<img src='");
        response.append("/fticons/");
        response.append(String.valueOf(hashCode));
        response.append("'/>");
        return response.toString();

    }

    private void getJsonResponseForPath(NavigationItem item, MyItemPresentation resultPresentation, String path) {
        if ((resultPresentation.navigation != null) && (resultPresentation.url != null)) {
            response.append("(");
            int position = path.indexOf(item.getName());
            if (position != -1) {
                path = path.substring(0, position);
            }
            position = path.indexOf("#anch");
            if (position != -1) {
                path = path.substring(0, position);
            }
            path = path.replace("/" + currentProject.getName(), "...");

            response.append(path);
            response.append(")");
        }
    }

    private void getJsonResponseForIcon(MyItemPresentation resultPresentation) {
        if (resultPresentation.icon != null) {
            response.append("<div style='display: inline;'>");
            if (resultPresentation.icon instanceof RowIcon) {
                for (int j = 0; j < ((RowIcon) resultPresentation.icon).getIconCount(); ++j) {
                    Icon icon = ((RowIcon) resultPresentation.icon).getIcon(j);
                    if (icon != null) {
                        response.append(getIconUrl(IconHelper.getInstance().addIconToMap(icon)));
                    }
                }
            } else {
                response.append(getIconUrl(IconHelper.getInstance().addIconToMap(resultPresentation.icon)));
            }
            response.append("</div>");
        }
    }

    private MyItemPresentation setResultPresentation(final PsiElement element, final ItemPresentation presentation) {
        final MyItemPresentation resultPresentation = new MyItemPresentation();
        resultPresentation.name = presentation.getPresentableText();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                resultPresentation.navigation = presentation.getLocationString();
                Icon icon = presentation.getIcon(true);
                if (icon instanceof DeferredIcon) {
                    resultPresentation.icon = ((DeferredIcon) icon).evaluate();
                } else {
                    resultPresentation.icon = icon;
                }

                VirtualFile file;
                int offset = 0;
                if (element instanceof PsiFile) {
                    file = ((PsiFile) element).getVirtualFile();
                } else {
                    file = PsiUtilBase.getVirtualFile(element);
                    offset = element.getTextOffset();
                }
                if (offset > 0) {
                    resultPresentation.url = file.getUrl() + "#anch" + offset + element.getContainingFile();
                } else {
                    resultPresentation.url = file.getUrl();
                }
                Module module = ModuleUtil.findModuleForFile(file, currentProject);
                if (module != null) {
                    resultPresentation.module = module.getName();
                   // resultPresentation.moduleIcon = module.getModuleType().getNodeIcon(true);
                }
            }
        });
        return resultPresentation;
    }

    private class MyItemPresentation {
        public String navigation;
        public String name;
        public String url;
        public Icon icon;
        public String module;
        public Icon moduleIcon;
    }
}
