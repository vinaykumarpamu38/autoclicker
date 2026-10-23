package com.techy_pvk.automate.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.techy_pvk.automate.data.InspectorResultStore;
import com.techy_pvk.automate.model.InspectedElement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InspectorViewModelTest {

    @Test
    public void wholeScreenScanWithResults() {
        List<InspectedElement> elements = new ArrayList<>(Collections.singletonList(new InspectedElement()));
        InspectorViewModel.UiState state = InspectorViewModel.toUiState(
                new InspectorResultStore.Result(elements, "Chrome", false));
        assertEquals("Elements found on Chrome", state.title);
        assertFalse(state.showEmptyMessage);
        assertEquals(1, state.elements.size());
    }

    @Test
    public void pointScanThatFoundNothingExplainsWhatToTry() {
        InspectorViewModel.UiState state = InspectorViewModel.toUiState(
                new InspectorResultStore.Result(new ArrayList<>(), "Maps", true));
        assertEquals("Element at your tap on Maps", state.title);
        assertTrue(state.showEmptyMessage);
        assertTrue(state.emptyMessage.contains("Show everything on screen"));
    }

    @Test
    public void wholeScreenScanThatFoundNothingPointsAtAccessibility() {
        InspectorViewModel.UiState state = InspectorViewModel.toUiState(
                new InspectorResultStore.Result(new ArrayList<>(), "Maps", false));
        assertTrue(state.showEmptyMessage);
        assertTrue(state.emptyMessage.contains("Accessibility"));
    }
}
