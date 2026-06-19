package com.mercury.chinesepinyinime;

import java.util.ArrayList;
import java.util.List;

public class CandidatePager {
    private final List<Integer> pageStarts = new ArrayList<>();

    public void recompute(String[] candidates, int availableWidth, CandidateWidthMeasurer measurer) {
        pageStarts.clear();
        if (candidates.length == 0) {
            pageStarts.add(0);
            return;
        }

        int index = 0;
        while (index < candidates.length) {
            pageStarts.add(index);
            int usedWidth = 0;
            int pageStart = index;
            while (index < candidates.length) {
                int candidateWidth = measurer.measure(candidates[index]);
                if (usedWidth + candidateWidth > availableWidth && index > pageStart) {
                    break;
                }
                usedWidth += candidateWidth;
                index++;
            }
        }
    }

    public int clampPageIndex(int pageIndex) {
        if (pageStarts.isEmpty()) {
            return 0;
        }
        if (pageIndex < 0) {
            return 0;
        }
        return Math.min(pageIndex, pageStarts.size() - 1);
    }

    public int getPageStart(int pageIndex) {
        if (pageStarts.isEmpty()) {
            return 0;
        }
        return pageStarts.get(clampPageIndex(pageIndex));
    }

    public int getPageEnd(int pageIndex, int candidateCount) {
        int safePageIndex = clampPageIndex(pageIndex);
        if (safePageIndex + 1 < pageStarts.size()) {
            return pageStarts.get(safePageIndex + 1);
        }
        return candidateCount;
    }

    public String[] getCandidatesForPage(String[] candidates, int pageIndex) {
        int start = getPageStart(pageIndex);
        int end = getPageEnd(pageIndex, candidates.length);
        String[] pageCandidates = new String[end - start];
        System.arraycopy(candidates, start, pageCandidates, 0, pageCandidates.length);
        return pageCandidates;
    }

    public boolean hasPreviousPage(int pageIndex) {
        return clampPageIndex(pageIndex) > 0;
    }

    public boolean hasNextPage(int pageIndex) {
        return clampPageIndex(pageIndex) + 1 < pageStarts.size();
    }

    public interface CandidateWidthMeasurer {
        int measure(String text);
    }
}
