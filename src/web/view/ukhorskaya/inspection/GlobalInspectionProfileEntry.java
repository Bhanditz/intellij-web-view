package web.view.ukhorskaya.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/18/11
 * Time: 6:22 PM
 */
public class GlobalInspectionProfileEntry {

    private static final GlobalInspectionProfileEntry ENTRY = new GlobalInspectionProfileEntry();
    private List<InspectionProfileEntry> profileEntryList = new ArrayList<InspectionProfileEntry>();

    public static GlobalInspectionProfileEntry getInstance() {
        return ENTRY;
    }

    public List<InspectionProfileEntry> getInspectionProfileEntry() {
        return profileEntryList;
    }

    private GlobalInspectionProfileEntry() {
        addToList(new RedundantCastInspection());
        addToList(new UnusedSymbolLocalInspection());
        addToList(new WrongPackageStatementInspection());
        addToList(new UnusedImportLocalInspection());
    }

    private void addToList(LocalInspectionTool tool) {
        HighlightDisplayKey.register(tool.getShortName());
        profileEntryList.add(new LocalInspectionToolWrapper(tool));
    }

}
