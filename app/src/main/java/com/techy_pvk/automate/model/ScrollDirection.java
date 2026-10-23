package com.techy_pvk.automate.model;

/**
 * Which way a SCROLL step reveals content. Named from the user's point of view (e.g. DOWN
 * = "reveal the content below", the same sense used by a scrollbar) - AutomationService
 * turns this into the correct on-screen finger-swipe direction, which for vertical scrolling
 * is the opposite of the direction named (scrolling down = the finger swipes up).
 */
public enum ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}
