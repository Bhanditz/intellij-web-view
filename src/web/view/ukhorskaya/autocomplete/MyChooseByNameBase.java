package web.view.ukhorskaya.autocomplete;

import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.CustomMatcherModel;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

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

public class MyChooseByNameBase {

    protected final Project myProject;
    protected final ChooseByNameModel myModel;
    private final Reference<PsiElement> myContext;

    private static final String EXTRA_ELEM = "...";

    private boolean myCheckboxState;

    private final String[][] myNames = new String[2][];
    private static final int MAXIMUM_LIST_SIZE_LIMIT = 30;
    protected final int myInitialIndex;

    private static class MatchesComparator implements Comparator<String> {
        private final String myOriginalPattern;

        private MatchesComparator(final String originalPattern) {
            myOriginalPattern = originalPattern.trim();
        }

        public int compare(final String a, final String b) {
            boolean aStarts = a.startsWith(myOriginalPattern);
            boolean bStarts = b.startsWith(myOriginalPattern);
            if (aStarts && bStarts) return a.compareToIgnoreCase(b);
            if (aStarts && !bStarts) return -1;
            if (bStarts && !aStarts) return 1;
            return a.compareToIgnoreCase(b);
        }
    }

    public MyChooseByNameBase(Project project, ChooseByNameModel model, final PsiElement context, boolean checkboxState) {
        this(project, model, context, checkboxState, 0);
    }

    public MyChooseByNameBase(Project project, ChooseByNameModel model, final PsiElement context, boolean checkboxState, final int initialIndex) {
        myProject = project;
        myModel = model;
        myContext = new WeakReference<PsiElement>(context);
        myInitialIndex = initialIndex;
        myCheckboxState = checkboxState;
        int index = checkboxState ? 1 : 0;
        myNames[index] = myModel.getNames(myCheckboxState);
    }

    private String getQualifierPattern(String pattern) {
        final String[] separators = myModel.getSeparators();
        int lastSeparatorOccurence = 0;
        for (String separator : separators) {
            lastSeparatorOccurence = Math.max(lastSeparatorOccurence, pattern.lastIndexOf(separator));
        }
        return pattern.substring(0, lastSeparatorOccurence);
    }

    public void addElementsByPattern(Set<Object> elementsArray, String pattern) {
        String namePattern = getNamePattern(pattern);
        String qualifierPattern = getQualifierPattern(pattern);

        //find pattern only for begining
        /*if (namePattern.trim().length() > 0) {
            namePattern = "*" + namePattern + "*";
        }*/

        boolean empty = namePattern.length() == 0 || namePattern.equals("@");    // TODO[yole]: remove implicit dependency
        if (empty) return;

        java.util.List<String> namesList = new ArrayList<String>();
        getNamesByPattern(namesList, namePattern);

        // Here we sort using namePattern to have similar logic with empty qualified patten case
        Collections.sort(namesList, new MatchesComparator(namePattern));

        boolean overflow = false;
        java.util.List<Object> sameNameElements = new SmartList<Object>();
        All:
        for (String name : namesList) {

            final Object[] elements = myModel.getElementsByName(name, myCheckboxState, namePattern);
            int myMaximumListSizeLimit = MAXIMUM_LIST_SIZE_LIMIT;
            if (elements.length > 1) {
                sameNameElements.clear();
                for (final Object element : elements) {
                    if (matchesQualifier(element, qualifierPattern)) {
                        sameNameElements.add(element);
                    }
                }
                sortByProximity(sameNameElements);
                for (Object element : sameNameElements) {
                    elementsArray.add(element);
                    if (elementsArray.size() >= myMaximumListSizeLimit) {
                        overflow = true;
                        break All;
                    }
                }
            } else if (elements.length == 1 && matchesQualifier(elements[0], qualifierPattern)) {
                elementsArray.add(elements[0]);
                if (elementsArray.size() >= myMaximumListSizeLimit) {
                    overflow = true;
                    break;
                }
            }
        }

        if (overflow) {
            elementsArray.add(EXTRA_ELEM);
        }
    }


    private void sortByProximity(final java.util.List<Object> sameNameElements) {
        Collections.sort(sameNameElements, new PathProximityComparator(myModel, myContext.get()));
    }

    private java.util.List<String> split(String s) {
        java.util.List<String> answer = new ArrayList<String>();
        for (String token : StringUtil.tokenize(s, StringUtil.join(myModel.getSeparators(), ""))) {
            if (token.length() > 0) {
                answer.add(token);
            }
        }

        return answer.isEmpty() ? Collections.singletonList(s) : answer;
    }

    private boolean matchesQualifier(final Object element, final String qualifierPattern) {
        final String name = myModel.getFullName(element);
        if (name == null) return false;

        final java.util.List<String> suspects = split(name);
        final java.util.List<com.intellij.openapi.util.Pair<String, NameUtil.Matcher>> patternsAndMatchers =
                ContainerUtil.map2List(split(qualifierPattern), new Function<String, com.intellij.openapi.util.Pair<String, NameUtil.Matcher>>() {
                    public com.intellij.openapi.util.Pair<String, NameUtil.Matcher> fun(String s) {
                        final String pattern = getNamePattern(s);
                        final NameUtil.Matcher matcher = buildPatternMatcher(pattern);

                        return new com.intellij.openapi.util.Pair<String, NameUtil.Matcher>(pattern, matcher);
                    }
                });

        int matchPosition = 0;

        try {
            patterns:
            for (com.intellij.openapi.util.Pair<String, NameUtil.Matcher> patternAndMatcher : patternsAndMatchers) {
                final String pattern = patternAndMatcher.first;
                final NameUtil.Matcher matcher = patternAndMatcher.second;
                if (pattern.length() > 0) {
                    for (int j = matchPosition; j < suspects.size() - 1; j++) {
                        String suspect = suspects.get(j);
                        if (matches(pattern, matcher, suspect)) {
                            matchPosition = j + 1;
                            continue patterns;
                        }
                    }

                    return false;
                }
            }
        } catch (Exception e) {
            // Do nothing. No matches appears valid result for "bad" pattern
            return false;
        }

        return true;
    }

    public void getNamesByPattern(
            final java.util.List<String> list,
            String pattern) throws ProcessCanceledException {


        if (pattern.startsWith("@")) {
            pattern = pattern.substring(1);
        }

        final String[] names = myCheckboxState ? myNames[1] : myNames[0];
        final NameUtil.Matcher matcher = buildPatternMatcher(pattern);

        try {
            for (String name : names) {
                if (matches(pattern, matcher, name)) {
                    list.add(name);
                }
            }
        } catch (Exception e) {
            // Do nothing. No matches appears valid result for "bad" pattern
        }
    }


    private boolean matches(String pattern, NameUtil.Matcher matcher, String name) {
        boolean matches = false;
        if (name != null) {
            if (myModel instanceof CustomMatcherModel) {
                if (((CustomMatcherModel) myModel).matches(name, pattern)) {
                    matches = true;
                }
            } else if (pattern.length() == 0 || matcher.matches(name)) {
                matches = true;
            }
        }
        return matches;
    }

    public String getNamePattern(String pattern) {
        final String[] separators = myModel.getSeparators();
        int lastSeparatorOccurence = 0;
        for (String separator : separators) {
            final int idx = pattern.lastIndexOf(separator);
            lastSeparatorOccurence = Math.max(lastSeparatorOccurence, idx == -1 ? idx : idx + separator.length());
        }

        return pattern.substring(lastSeparatorOccurence);
    }


    private static NameUtil.Matcher buildPatternMatcher(String pattern) {
        return NameUtil.buildMatcher(pattern, 0, true, true, pattern.toLowerCase().equals(pattern));
    }

    private static class PathProximityComparator implements Comparator<Object> {
        private final ChooseByNameModel myModel;
        private final PsiProximityComparator myProximityComparator;

        private PathProximityComparator(final ChooseByNameModel model, @Nullable final PsiElement context) {
            myModel = model;
            myProximityComparator = new PsiProximityComparator(context);
        }

        public int compare(final Object o1, final Object o2) {
            int rc = myProximityComparator.compare(o1, o2);
            if (rc != 0) return rc;

            return Comparing.compare(myModel.getFullName(o1), myModel.getFullName(o2));
        }
    }

}


