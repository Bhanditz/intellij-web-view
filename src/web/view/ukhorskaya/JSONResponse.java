package web.view.ukhorskaya;

import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
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
public class JSONResponse {

    private FilteringGotoByModel myModel;

    public JSONResponse(FilteringGotoByModel model) {
        this.myModel = model;
    }

    public String getResponse(final Project currentProject, PsiFile psiFile, final String term) {
        StringBuilder response = new StringBuilder();
        final MyChooseByNameBase chooser = new MyChooseByNameBase(currentProject, myModel, psiFile, false);
        //final ArrayList<String> list = new ArrayList<String>();
        final HashSet<Object> set = new HashSet<Object>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
                //chooser.getNamesByPattern(list, term);
                chooser.addElementsByPattern(set, term);
            }
        });

        final Object[] list = set.toArray();

        response.append("[");
        //for (int i = 0; i < 50 && i < list.size(); i++) {
        for (int i = 0; i < 30 && i < list.length; i++) {
            if (list[i].toString().equals("...")) {
                continue;
            }
            final NavigationItem item = (NavigationItem) list[i];
            final PsiElement element = (PsiElement) list[i];
            final ItemPresentation presentation = item.getPresentation();
            if (item.getPresentation() == null) {
                continue;
            }

            final Ref<String> refNavigation = new Ref<String>();
            final Ref<String> refUrl = new Ref<String>();
            final Ref<Icon> refIcon = new Ref<Icon>();
            final Ref<String> refModule = new Ref<String>();
            final Ref<Icon> refModuleIcon = new Ref<Icon>();

            ApplicationManager.getApplication().runReadAction(new Runnable() {
                public void run() {
                    refNavigation.set(presentation.getLocationString());
                    refIcon.set(presentation.getIcon(true));
                    Icon icon = presentation.getIcon(true);
                    if (icon instanceof DeferredIcon) {
                        refIcon.set(((DeferredIcon) icon).evaluate());
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
                        refUrl.set(file.getPath() + "#anch" + offset + element.getContainingFile());
                    } else {
                        refUrl.set(file.getPath());
                    }
                    Module module = ModuleUtil.findModuleForFile(file, currentProject);
                    if (module != null) {
                        refModule.set(module.getName());
                        refModuleIcon.set(module.getModuleType().getNodeIcon(true));
                    }
                }
            });

            response.append("{\"label\":\"");
            response.append(presentation.getPresentableText());
            response.append("\", \"icon\":\"");
            if (refIcon.get() != null) {
                response.append("<div style='display: inline;'>");
                if (refIcon.get() instanceof RowIcon) {
                    for (int j = 0; j < ((RowIcon) refIcon.get()).getIconCount(); ++j) {
                        MyIcon myIcon = new MyIcon(((RowIcon) refIcon.get()).getIcon(j));
                        response.append(myIcon.getIconUrl(((RowIcon) refIcon.get()).getIcon(j)));
                    }
                } else {
                    MyIcon myIcon = new MyIcon(refIcon.get());
                    response.append(myIcon.getIconUrl(refIcon.get()));
                }
                response.append("</div>");
            }

            String path = "";
            response.append("\", \"url\":\"");
            if (refUrl.get() != null) {
                path = refUrl.get();
                if (currentProject != null) {
                    String projectDir = currentProject.getBaseDir().getPath();
                    if (path.contains(projectDir)) {
                        path = path.substring(path.indexOf(currentProject.getName()) - 1);
                        response.append(path);
                    }
                }
            }

            response.append("\", \"path\":\"");
            if ((refNavigation.get() != null) && (refUrl.get() != null)) {
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
            response.append("\", \"moduleName\":\"");
            response.append("<div style='float: right;'>");
            if (refModule.get() != null) {
                response.append(refModule.get());
            }
            if (refModuleIcon.get() != null) {
                response.append("       ");
                MyIcon myIcon = new MyIcon(refModuleIcon.get());
                response.append(myIcon.getIconUrl(refModuleIcon.get()));
            }
            response.append("</div>");
            response.append("\"},");
        }

        response.delete(response.length() - 1, response.length());
        if (response.length() == 0) {
            response.append("[");
            response.append("{\"label\":\"null\"}");
        }
        response.append("]");

        return response.toString();
    }
}
