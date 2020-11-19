package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;

import com.android.launcher3.util.LauncherEdgeEffect;
import com.android.launcher3.util.Log;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;

public abstract class PagedView extends ViewGroup implements ViewGroup.OnHierarchyChangeListener {
    private static final String TAG = "PagedView";
    private static final boolean DEBUG = true;
    protected static final int INVALID_PAGE = -1;

    // the min drag distance for a fling to register, to prevent random page shifts
    private static final int MIN_LENGTH_FOR_FLING = 25;

    protected static final int PAGE_SNAP_ANIMATION_DURATION = 750;
    protected static final int SLOW_PAGE_SNAP_ANIMATION_DURATION = 950;
    protected static final float NANOTIME_DIV = 1000000000.0f;

    private static final float RETURN_TO_ORIGINAL_PAGE_THRESHOLD = 0.33f;
    // The page is moved more than halfway, automatically move to the next page on touch up.
    private static final float SIGNIFICANT_MOVE_THRESHOLD = 0.4f;

    private static final float MAX_SCROLL_PROGRESS = 1.0f;

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_SNAP_VELOCITY = 1500;
    private static final int MIN_FLING_VELOCITY = 250;

    public static final int INVALID_RESTORE_PAGE = -1001;

    private boolean mFreeScroll = false;
    private int mFreeScrollMinScrollX = -1;
    private int mFreeScrollMaxScrollX = -1;

    static final int AUTOMATIC_PAGE_SPACING = -1;

    protected int mFlingThresholdVelocity;
    protected int mMinFlingVelocity;
    protected int mMinSnapVelocity;

    protected float mDensity;
    protected float mSmoothingTime;
    protected float mTouchX;

    protected boolean mFirstLayout = true;
    private int mNormalChildHeight;

    protected int mCurrentPage;
    protected int mRestorePage = INVALID_RESTORE_PAGE;
    protected int mChildCountOnLastLayout;

    protected int mNextPage = INVALID_PAGE;
    protected int mMaxScrollX;
    protected LauncherScroller mScroller;
    private Interpolator mDefaultInterpolator;
    private VelocityTracker mVelocityTracker;
    @Thunk int mPageSpacing = 0;

    private float mParentDownMotionX;
    private float mParentDownMotionY;
    private float mDownMotionX;
    private float mDownMotionY;
    private float mDownScrollX;
    private float mDragViewBaselineLeft;
    protected float mLastMotionX;
    protected float mLastMotionXRemainder;
    protected float mLastMotionY;
    protected float mTotalMotionX;
    private int mLastScreenCenter = -1;

    private boolean mCancelTap;

    private int[] mPageScrolls;

    protected final static int TOUCH_STATE_REST = 0;    //初始状态
    protected final static int TOUCH_STATE_SCROLLING = 1;   //滚动状态
    protected final static int TOUCH_STATE_PREV_PAGE = 2;   //向前翻页状态
    protected final static int TOUCH_STATE_NEXT_PAGE = 3;   //向后翻页状态
    //长按桌面的情况下，workspace缩小，此时你可以长按CellLayout拖动进行排序，
    //因此出现了这个排序状态。如果只是滑动，则为滚动状态。
    protected final static int TOUCH_STATE_REORDERING = 4;  //排序状态

    protected final static float ALPHA_QUANTIZE_LEVEL = 0.0001f;

    //记录状态
    protected int mTouchState = TOUCH_STATE_REST;
    protected boolean mForceScreenScrolled = false;

    protected OnLongClickListener mLongClickListener;

    protected int mTouchSlop;
    private int mMaximumVelocity;
    protected int mPageLayoutWidthGap;
    protected int mPageLayoutHeightGap;
    protected int mCellCountX = 0;
    protected int mCellCountY = 0;
    protected boolean mCenterPagesVertically;
    protected boolean mAllowOverScroll = true;
    protected int[] mTempVisiblePagesRange = new int[2];
    protected boolean mForceDrawAllChildrenNextFrame;

    protected static final int INVALID_POINTER = -1;

    protected int mActivePointerId = INVALID_POINTER;

    private PageSwitchListener mPageSwitchListener;

    // If true, modify alpha of neighboring pages as user scrolls left/right
    protected boolean mFadeInAdjacentScreens = false;

    protected boolean mIsPageMoving = false;

    private boolean mWasInOverscroll = false;

    // Page Indicator
    @Thunk int mPageIndicatorViewId;
    @Thunk PageIndicator mPageIndicator;
    // The viewport whether the pages are to be contained (the actual view may be larger than the
    // viewport)
    private Rect mViewport = new Rect();

    // Reordering
    // We use the min scale to determine how much to expand the actually PagedView measured
    // dimensions such that when we are zoomed out, the view is not clipped
    private static int REORDERING_DROP_REPOSITION_DURATION = 200;
    @Thunk static int REORDERING_REORDER_REPOSITION_DURATION = 300;
    private static int REORDERING_SIDE_PAGE_HOVER_TIMEOUT = 80;

    private float mMinScale = 1f;
    private boolean mUseMinScale = false;
    protected View mDragView;
    private Runnable mSidePageHoverRunnable;
    @Thunk int mSidePageHoverIndex = -1;
    // This variable's scope is only for the duration of startReordering() and endReordering()
    private boolean mReorderingStarted = false;
    // This variable's scope is for the duration of startReordering() and after the zoomIn()
    // animation after endReordering()
    private boolean mIsReordering;
    // The runnable that settles the page after snapToPage and animateDragViewToOriginalPosition
    private int NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT = 2;
    private int mPostReorderingPreZoomInRemainingAnimationCount;
    private Runnable mPostReorderingPreZoomInRunnable;

    // Convenience/caching
    private static final Matrix sTmpInvMatrix = new Matrix();
    private static final float[] sTmpPoint = new float[2];
    private static final int[] sTmpIntPoint = new int[2];
    private static final Rect sTmpRect = new Rect();

    protected final Rect mInsets = new Rect();
    protected final boolean mIsRtl;

    // Edge effect
    private final LauncherEdgeEffect mEdgeGlowLeft = new LauncherEdgeEffect();
    private final LauncherEdgeEffect mEdgeGlowRight = new LauncherEdgeEffect();

    public interface PageSwitchListener {
        void onPageSwitch(View newPage, int newPageIndex);
    }

    public PagedView(Context context) {
        this(context, null);
    }

    public PagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.PagedView, defStyle, 0);

        mPageLayoutWidthGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutWidthGap, 0);
        mPageLayoutHeightGap = a.getDimensionPixelSize(
                R.styleable.PagedView_pageLayoutHeightGap, 0);
        mPageIndicatorViewId = a.getResourceId(R.styleable.PagedView_pageIndicator, -1);
        a.recycle();

        setHapticFeedbackEnabled(false);
        mIsRtl = Utilities.isRtl(getResources());
        init();
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void init() {
        mScroller = new LauncherScroller(getContext());
        setDefaultInterpolator(new ScrollInterpolator());
        mCurrentPage = 0;
        mCenterPagesVertically = true;

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mDensity = getResources().getDisplayMetrics().density;

        mFlingThresholdVelocity = (int) (FLING_THRESHOLD_VELOCITY * mDensity);
        mMinFlingVelocity = (int) (MIN_FLING_VELOCITY * mDensity);
        mMinSnapVelocity = (int) (MIN_SNAP_VELOCITY * mDensity);
        setOnHierarchyChangeListener(this);
        setWillNotDraw(false);
    }

    protected void setEdgeGlowColor(int color) {
        mEdgeGlowLeft.setColor(color);
        mEdgeGlowRight.setColor(color);
    }

    protected void setDefaultInterpolator(Interpolator interpolator) {
        mDefaultInterpolator = interpolator;
        mScroller.setInterpolator(mDefaultInterpolator);
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Hook up the page indicator
        ViewGroup parent = (ViewGroup) getParent();
        ViewGroup grandParent = (ViewGroup) parent.getParent();
        if (mPageIndicator == null && mPageIndicatorViewId > -1) {
            mPageIndicator = (PageIndicator) grandParent.findViewById(mPageIndicatorViewId);
            mPageIndicator.removeAllMarkers(true);

            ArrayList<PageIndicator.PageMarkerResources> markers =
                    new ArrayList<PageIndicator.PageMarkerResources>();
            for (int i = 0; i < getChildCount(); ++i) {
                markers.add(getPageIndicatorMarker(i));
            }

            mPageIndicator.addMarkers(markers, true);

            OnClickListener listener = getPageIndicatorClickListener();
            if (listener != null) {
                mPageIndicator.setOnClickListener(listener);
            }
            mPageIndicator.setContentDescription(getPageIndicatorDescription());
        }
    }

    protected String getPageIndicatorDescription() {
        return getCurrentPageDescription();
    }

    protected OnClickListener getPageIndicatorClickListener() {
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Unhook the page indicator
        mPageIndicator = null;
    }

    // Convenience methods to map points from self to parent and vice versa
    private float[] mapPointFromViewToParent(View v, float x, float y) {
        sTmpPoint[0] = x;
        sTmpPoint[1] = y;
        v.getMatrix().mapPoints(sTmpPoint);
        sTmpPoint[0] += v.getLeft();
        sTmpPoint[1] += v.getTop();
        return sTmpPoint;
    }
    private float[] mapPointFromParentToView(View v, float x, float y) {
        sTmpPoint[0] = x - v.getLeft();
        sTmpPoint[1] = y - v.getTop();
        v.getMatrix().invert(sTmpInvMatrix);
        sTmpInvMatrix.mapPoints(sTmpPoint);
        return sTmpPoint;
    }

    private void updateDragViewTranslationDuringDrag() {
        if (mDragView != null) {
            float x = (mLastMotionX - mDownMotionX) + (getScrollX() - mDownScrollX) +
                    (mDragViewBaselineLeft - mDragView.getLeft());
            float y = mLastMotionY - mDownMotionY;
            mDragView.setTranslationX(x);
            mDragView.setTranslationY(y);

            if (DEBUG) Log.d(TAG, "PagedView.updateDragViewTranslationDuringDrag(): "
                    + x + ", " + y);
        }
    }

    public void setMinScale(float f) {
        mMinScale = f;
        mUseMinScale = true;
        requestLayout();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        if (isReordering(true)) {
            float[] p = mapPointFromParentToView(this, mParentDownMotionX, mParentDownMotionY);
            mLastMotionX = p[0];
            mLastMotionY = p[1];
            updateDragViewTranslationDuringDrag();
        }
    }

    // Convenience methods to get the actual width/height of the PagedView (since it is measured
    // to be larger to account for the minimum possible scale)
    int getViewportWidth() {
        return mViewport.width();
    }
    int getViewportHeight() {
        return mViewport.height();
    }

    // Convenience methods to get the offset ASSUMING that we are centering the pages in the
    // PagedView both horizontally and vertically
    int getViewportOffsetX() {
        return (getMeasuredWidth() - getViewportWidth()) / 2;
    }

    int getViewportOffsetY() {
        return (getMeasuredHeight() - getViewportHeight()) / 2;
    }

    PageIndicator getPageIndicator() {
        return mPageIndicator;
    }
    protected PageIndicator.PageMarkerResources getPageIndicatorMarker(int pageIndex) {
        return new PageIndicator.PageMarkerResources();
    }

    /**
     * Add a page change listener which will be called when a page is _finished_ listening.
     *
     */
    public void setPageSwitchListener(PageSwitchListener pageSwitchListener) {
        mPageSwitchListener = pageSwitchListener;
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(mCurrentPage), mCurrentPage);
        }
    }

    /**
     * Returns the index of the currently displayed page.
     */
    public int getCurrentPage() {
        return mCurrentPage;
    }

    /**
     * Returns the index of page to be shown immediately afterwards.
     */
    int getNextPage() {
        return (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
    }

    int getPageCount() {
        return getChildCount();
    }

    public View getPageAt(int index) {
        return getChildAt(index);
    }

    protected int indexToPage(int index) {
        return index;
    }

    /**
     * Updates the scroll of the current page immediately to its final scroll position.  We use this
     * in CustomizePagedView to allow tabs to share the same PagedView while resetting the scroll of
     * the previous tab page.
     */
    protected void updateCurrentPageScroll() {
        // If the current page is invalid, just reset the scroll position to zero
        int newX = 0;
        if (0 <= mCurrentPage && mCurrentPage < getPageCount()) {
            newX = getScrollForPage(mCurrentPage);
        }
        scrollTo(newX, 0);
        mScroller.setFinalX(newX);
        forceFinishScroller();
    }

    private void abortScrollerAnimation(boolean resetNextPage) {
        mScroller.abortAnimation();
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        if (resetNextPage) {
            mNextPage = INVALID_PAGE;
        }
    }

    private void forceFinishScroller() {
        mScroller.forceFinished(true);
        // We need to clean up the next page here to avoid computeScrollHelper from
        // updating current page on the pass.
        mNextPage = INVALID_PAGE;
    }

    private int validateNewPage(int newPage) {
        int validatedPage = newPage;
        // When in free scroll mode, we need to clamp to the free scroll page range.
        if (mFreeScroll) {
            getFreeScrollPageRange(mTempVisiblePagesRange);
            validatedPage = Math.max(mTempVisiblePagesRange[0],
                    Math.min(newPage, mTempVisiblePagesRange[1]));
        }
        // Ensure that it is clamped by the actual set of children in all cases
        validatedPage = Math.max(0, Math.min(validatedPage, getPageCount() - 1));
        return validatedPage;
    }

    /**
     * Sets the current page.
     */
    public void setCurrentPage(int currentPage) {
        if (!mScroller.isFinished()) {
            abortScrollerAnimation(true);
        }
        // don't introduce any checks like mCurrentPage == currentPage here-- if we change the
        // the default
        if (getChildCount() == 0) {
            return;
        }
        mForceScreenScrolled = true;
        mCurrentPage = validateNewPage(currentPage);
        updateCurrentPageScroll();
        notifyPageSwitchListener();
        invalidate();
    }

    /**
     * The restore page will be set in place of the current page at the next (likely first)
     * layout.
     */
    void setRestorePage(int restorePage) {
        mRestorePage = restorePage;
    }
    int getRestorePage() {
        return mRestorePage;
    }

    /**
     * Should be called whenever the page changes. In the case of a scroll, we wait until the page
     * has settled.
     */
    protected void notifyPageSwitchListener() {
        if (mPageSwitchListener != null) {
            mPageSwitchListener.onPageSwitch(getPageAt(getNextPage()), getNextPage());
        }

        updatePageIndicator();
    }

    private void updatePageIndicator() {
        // Update the page indicator (when we aren't reordering)
        if (mPageIndicator != null) {
            mPageIndicator.setContentDescription(getPageIndicatorDescription());
            if (!isReordering(false)) {
                mPageIndicator.setActiveMarker(getNextPage());
            }
        }
    }
    protected void pageBeginMoving() {
        if (!mIsPageMoving) {
            mIsPageMoving = true;
            onPageBeginMoving();
        }
    }

    protected void pageEndMoving() {
        if (mIsPageMoving) {
            mIsPageMoving = false;
            onPageEndMoving();
        }
    }

    protected boolean isPageMoving() {
        return mIsPageMoving;
    }

    // a method that subclasses can override to add behavior
    protected void onPageBeginMoving() {
    }

    // a method that subclasses can override to add behavior
    protected void onPageEndMoving() {
        mWasInOverscroll = false;
    }

    /**
     * Registers the specified listener on each page contained in this workspace.
     *
     * @param l The listener used to respond to long clicks.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mLongClickListener = l;
        final int count = getPageCount();
        for (int i = 0; i < count; i++) {
            getPageAt(i).setOnLongClickListener(l);
        }
        super.setOnLongClickListener(l);
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(getScrollX() + x, getScrollY() + y);
    }

    @Override
    public void scrollTo(int x, int y) {
        // In free scroll mode, we clamp the scrollX
        if (mFreeScroll) {
            // If the scroller is trying to move to a location beyond the maximum allowed
            // in the free scroll mode, we make sure to end the scroll operation.
            if (!mScroller.isFinished() &&
                    (x > mFreeScrollMaxScrollX || x < mFreeScrollMinScrollX)) {
                forceFinishScroller();
            }

            x = Math.min(x, mFreeScrollMaxScrollX);
            x = Math.max(x, mFreeScrollMinScrollX);
        }

        boolean isXBeforeFirstPage = mIsRtl ? (x > mMaxScrollX) : (x < 0);
        boolean isXAfterLastPage = mIsRtl ? (x < 0) : (x > mMaxScrollX);
        if (isXBeforeFirstPage) {
            super.scrollTo(mIsRtl ? mMaxScrollX : 0, y);
            if (mAllowOverScroll) {
                mWasInOverscroll = true;
                if (mIsRtl) {
                    overScroll(x - mMaxScrollX);
                } else {
                    overScroll(x);
                }
            }
        } else if (isXAfterLastPage) {
            super.scrollTo(mIsRtl ? 0 : mMaxScrollX, y);
            if (mAllowOverScroll) {
                mWasInOverscroll = true;
                if (mIsRtl) {
                    overScroll(x);
                } else {
                    overScroll(x - mMaxScrollX);
                }
            }
        } else {
            if (mWasInOverscroll) {
                overScroll(0);
                mWasInOverscroll = false;
            }
            super.scrollTo(x, y);
        }

        mTouchX = x;
        mSmoothingTime = System.nanoTime() / NANOTIME_DIV;

        // Update the last motion events when scrolling
        if (isReordering(true)) {
            float[] p = mapPointFromParentToView(this, mParentDownMotionX, mParentDownMotionY);
            mLastMotionX = p[0];
            mLastMotionY = p[1];
            updateDragViewTranslationDuringDrag();
        }
    }

    private void sendScrollAccessibilityEvent() {
        AccessibilityManager am =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am.isEnabled()) {
            if (mCurrentPage != getNextPage()) {
                AccessibilityEvent ev =
                        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
                ev.setScrollable(true);
                ev.setScrollX(getScrollX());
                ev.setScrollY(getScrollY());
                ev.setMaxScrollX(mMaxScrollX);
                ev.setMaxScrollY(0);

                sendAccessibilityEventUnchecked(ev);
            }
        }
    }

    // we moved this functionality to a helper function so SmoothPagedView can reuse it
    protected boolean computeScrollHelper() {
        if (mScroller.computeScrollOffset()) {
            // Don't bother scrolling if the page does not need to be moved
            if (getScrollX() != mScroller.getCurrX()
                || getScrollY() != mScroller.getCurrY()) {
                float scaleX = mFreeScroll ? getScaleX() : 1f;
                int scrollX = (int) (mScroller.getCurrX() * (1 / scaleX));
                scrollTo(scrollX, mScroller.getCurrY());
            }
            invalidate();
            return true;
        } else if (mNextPage != INVALID_PAGE) {
            sendScrollAccessibilityEvent();

            mCurrentPage = validateNewPage(mNextPage);
            mNextPage = INVALID_PAGE;
            notifyPageSwitchListener();

            // We don't want to trigger a page end moving unless the page has settled
            // and the user has stopped scrolling
            if (mTouchState == TOUCH_STATE_REST) {
                pageEndMoving();
            }

            onPostReorderingAnimationCompleted();
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am.isEnabled()) {
                // Notify the user when the page changes
                announceForAccessibility(getCurrentPageDescription());
            }
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        computeScrollHelper();
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public boolean isFullScreenPage = false;

        /**
         * {@inheritDoc}
         */
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    public void addFullScreenPage(View page) {
        LayoutParams lp = generateDefaultLayoutParams();
        lp.isFullScreenPage = true;
        super.addView(page, 0, lp);
    }

    public int getNormalChildHeight() {
        return mNormalChildHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // We measure the dimensions of the PagedView to be larger than the pages so that when we
        // zoom out (and scale down), the view is still contained in the parent
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        // NOTE: We multiply by 2f to account for the fact that depending on the offset of the
        // viewport, we can be at most one and a half screens offset once we scale down
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxSize = Math.max(dm.widthPixels + mInsets.left + mInsets.right,
                dm.heightPixels + mInsets.top + mInsets.bottom);

        int parentWidthSize = (int) (2f * maxSize);
        int parentHeightSize = (int) (2f * maxSize);
        int scaledWidthSize, scaledHeightSize;
        if (mUseMinScale) {
            scaledWidthSize = (int) (parentWidthSize / mMinScale);
            scaledHeightSize = (int) (parentHeightSize / mMinScale);
        } else {
            scaledWidthSize = widthSize;
            scaledHeightSize = heightSize;
        }
        mViewport.set(0, 0, widthSize, heightSize);

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        /* Allow the height to be set as WRAP_CONTENT. This allows the particular case
         * of the All apps view on XLarge displays to not take up more space then it needs. Width
         * is still not allowed to be set as WRAP_CONTENT since many parts of the code expect
         * each page to have the same width.
         */
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();

        int referenceChildWidth = 0;
        // The children are given the same width and height as the workspace
        // unless they were set to WRAP_CONTENT
        if (DEBUG) Log.d(TAG, "PagedView.onMeasure(): " + widthSize + ", " + heightSize);
        if (DEBUG) Log.d(TAG, "PagedView.scaledSize: " + scaledWidthSize + ", " + scaledHeightSize);
        if (DEBUG) Log.d(TAG, "PagedView.parentSize: " + parentWidthSize + ", " + parentHeightSize);
        if (DEBUG) Log.d(TAG, "PagedView.horizontalPadding: " + horizontalPadding);
        if (DEBUG) Log.d(TAG, "PagedView.verticalPadding: " + verticalPadding);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            // disallowing padding in paged view (just pass 0)
            final View child = getPageAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                int childWidthMode;
                int childHeightMode;
                int childWidth;
                int childHeight;

                if (!lp.isFullScreenPage) {
                    if (lp.width == LayoutParams.WRAP_CONTENT) {
                        childWidthMode = MeasureSpec.AT_MOST;
                    } else {
                        childWidthMode = MeasureSpec.EXACTLY;
                    }

                    if (lp.height == LayoutParams.WRAP_CONTENT) {
                        childHeightMode = MeasureSpec.AT_MOST;
                    } else {
                        childHeightMode = MeasureSpec.EXACTLY;
                    }

                    childWidth = getViewportWidth() - horizontalPadding
                            - mInsets.left - mInsets.right;
                    childHeight = getViewportHeight() - verticalPadding
                            - mInsets.top - mInsets.bottom;
                    mNormalChildHeight = childHeight;
                } else {
                    childWidthMode = MeasureSpec.EXACTLY;
                    childHeightMode = MeasureSpec.EXACTLY;

                    childWidth = getViewportWidth() - mInsets.left - mInsets.right;
                    childHeight = getViewportHeight();
                }
                if (referenceChildWidth == 0) {
                    referenceChildWidth = childWidth;
                }

                final int childWidthMeasureSpec =
                        MeasureSpec.makeMeasureSpec(childWidth, childWidthMode);
                    final int childHeightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(childHeight, childHeightMode);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
        setMeasuredDimension(scaledWidthSize, scaledHeightSize);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            return;
        }

        if (DEBUG) Log.d(TAG, "PagedView.onLayout()");
        final int childCount = getChildCount();

        int offsetX = getViewportOffsetX();
        int offsetY = getViewportOffsetY();

        // Update the viewport offsets
        mViewport.offset(offsetX, offsetY);

        final int startIndex = mIsRtl ? childCount - 1 : 0;
        final int endIndex = mIsRtl ? -1 : childCount;
        final int delta = mIsRtl ? -1 : 1;

        int verticalPadding = getPaddingTop() + getPaddingBottom();

        LayoutParams lp = (LayoutParams) getChildAt(startIndex).getLayoutParams();
        LayoutParams nextLp;

        int childLeft = offsetX + (lp.isFullScreenPage ? 0 : getPaddingLeft());
        if (mPageScrolls == null || childCount != mChildCountOnLastLayout) {
            mPageScrolls = new int[childCount];
        }

        for (int i = startIndex; i != endIndex; i += delta) {
            final View child = getPageAt(i);
            if (child.getVisibility() != View.GONE) {
                lp = (LayoutParams) child.getLayoutParams();
                int childTop;
                if (lp.isFullScreenPage) {
                    childTop = offsetY;
                } else {
                    childTop = offsetY + getPaddingTop() + mInsets.top;
                    if (mCenterPagesVertically) {
                        childTop += (getViewportHeight() - mInsets.top - mInsets.bottom - verticalPadding - child.getMeasuredHeight()) / 2;
                    }
                }

                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                if (DEBUG) Log.d(TAG, "\tlayout-child" + i + ": " + childLeft + ", " + childTop);
                child.layout(childLeft, childTop,
                        childLeft + child.getMeasuredWidth(), childTop + childHeight);

                int scrollOffsetLeft = lp.isFullScreenPage ? 0 : getPaddingLeft();
                mPageScrolls[i] = childLeft - scrollOffsetLeft - offsetX;

                int pageGap = mPageSpacing;
                int next = i + delta;
                if (next != endIndex) {
                    nextLp = (LayoutParams) getPageAt(next).getLayoutParams();
                } else {
                    nextLp = null;
                }

                // Prevent full screen pages from showing in the viewport
                // when they are not the current page.
                if (lp.isFullScreenPage) {
                    pageGap = getPaddingLeft();
                } else if (nextLp != null && nextLp.isFullScreenPage) {
                    pageGap = getPaddingRight();
                }

                childLeft += childWidth + pageGap + getChildGap();
            }
        }

        final LayoutTransition transition = getLayoutTransition();
        // If the transition is running defer updating max scroll, as some empty pages could
        // still be present, and a max scroll change could cause sudden jumps in scroll.
        if (transition != null && transition.isRunning()) {
            transition.addTransitionListener(new LayoutTransition.TransitionListener() {

                @Override
                public void startTransition(LayoutTransition transition, ViewGroup container,
                        View view, int transitionType) { }

                @Override
                public void endTransition(LayoutTransition transition, ViewGroup container,
                        View view, int transitionType) {
                    // Wait until all transitions are complete.
                    if (!transition.isRunning()) {
                        transition.removeTransitionListener(this);
                        updateMaxScrollX();
                    }
                }
            });
        } else {
            updateMaxScrollX();
        }

        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < childCount) {
            updateCurrentPageScroll();
            mFirstLayout = false;
        }

        if (mScroller.isFinished() && mChildCountOnLastLayout != childCount) {
            if (mRestorePage != INVALID_RESTORE_PAGE) {
                setCurrentPage(mRestorePage);
                mRestorePage = INVALID_RESTORE_PAGE;
            } else {
                setCurrentPage(getNextPage());
            }
        }
        mChildCountOnLastLayout = childCount;

        if (isReordering(true)) {
            updateDragViewTranslationDuringDrag();
        }
    }

    protected int getChildGap() {
        return 0;
    }

    @Thunk void updateMaxScrollX() {
        int childCount = getChildCount();
        if (childCount > 0) {
            final int index = mIsRtl ? 0 : childCount - 1;
            mMaxScrollX = getScrollForPage(index);
        } else {
            mMaxScrollX = 0;
        }
    }

    public void setPageSpacing(int pageSpacing) {
        mPageSpacing = pageSpacing;
        requestLayout();
    }

    /**
     * Called when the center screen changes during scrolling.
     */
    protected void screenScrolled(int screenCenter) { }

    @Override
    public void onChildViewAdded(View parent, View child) {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null && !isReordering(false)) {
            int pageIndex = indexOfChild(child);
            mPageIndicator.addMarker(pageIndex,
                    getPageIndicatorMarker(pageIndex),
                    true);
        }

        // This ensures that when children are added, they get the correct transforms / alphas
        // in accordance with any scroll effects.
        mForceScreenScrolled = true;
        updateFreescrollBounds();
        invalidate();
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        mForceScreenScrolled = true;
        updateFreescrollBounds();
        invalidate();
    }

    private void removeMarkerForView(int index) {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null && !isReordering(false)) {
            mPageIndicator.removeMarker(index, true);
        }
    }

    @Override
    public void removeView(View v) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView(indexOfChild(v));
        super.removeView(v);
    }
    @Override
    public void removeViewInLayout(View v) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView(indexOfChild(v));
        super.removeViewInLayout(v);
    }
    @Override
    public void removeViewAt(int index) {
        // XXX: We should find a better way to hook into this before the view
        // gets removed form its parent...
        removeMarkerForView(index);
        super.removeViewAt(index);
    }
    @Override
    public void removeAllViewsInLayout() {
        // Update the page indicator, we don't update the page indicator as we
        // add/remove pages
        if (mPageIndicator != null) {
            mPageIndicator.removeAllMarkers(true);
        }

        super.removeAllViewsInLayout();
    }

    protected int getChildOffset(int index) {
        if (index < 0 || index > getChildCount() - 1) return 0;

        int offset = getPageAt(index).getLeft() - getViewportOffsetX();

        return offset;
    }

    protected void getFreeScrollPageRange(int[] range) {
        range[0] = 0;
        range[1] = Math.max(0, getChildCount() - 1);
    }

    protected void getVisiblePages(int[] range) {
        final int pageCount = getChildCount();
        sTmpIntPoint[0] = sTmpIntPoint[1] = 0;

        range[0] = -1;
        range[1] = -1;

        if (pageCount > 0) {
            int viewportWidth = getViewportWidth();
            int curScreen = 0;

            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View currPage = getPageAt(i);

                sTmpIntPoint[0] = 0;
                Utilities.getDescendantCoordRelativeToParent(currPage, this, sTmpIntPoint, false);
                if (sTmpIntPoint[0] > viewportWidth) {
                    if (range[0] == -1) {
                        continue;
                    } else {
                        break;
                    }
                }

                sTmpIntPoint[0] = currPage.getMeasuredWidth();
                Utilities.getDescendantCoordRelativeToParent(currPage, this, sTmpIntPoint, false);
                if (sTmpIntPoint[0] < 0) {
                    if (range[0] == -1) {
                        continue;
                    } else {
                        break;
                    }
                }
                curScreen = i;
                if (range[0] < 0) {
                    range[0] = curScreen;
                }
            }

            range[1] = curScreen;
        } else {
            range[0] = -1;
            range[1] = -1;
        }
    }

    protected boolean shouldDrawChild(View child) {
        return child.getVisibility() == VISIBLE;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Find out which screens are visible; as an optimization we only call draw on them
        final int pageCount = getChildCount();
        if (pageCount > 0) {
            int halfScreenSize = getViewportWidth() / 2;
            int screenCenter = getScrollX() + halfScreenSize;

            if (screenCenter != mLastScreenCenter || mForceScreenScrolled) {
                // set mForceScreenScrolled before calling screenScrolled so that screenScrolled can
                // set it for the next frame
                mForceScreenScrolled = false;
                screenScrolled(screenCenter);
                mLastScreenCenter = screenCenter;
            }

            getVisiblePages(mTempVisiblePagesRange);
            final int leftScreen = mTempVisiblePagesRange[0];
            final int rightScreen = mTempVisiblePagesRange[1];
            if (leftScreen != -1 && rightScreen != -1) {
                final long drawingTime = getDrawingTime();
                // Clip to the bounds
                canvas.save();
                canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getRight() - getLeft(),
                        getScrollY() + getBottom() - getTop());

                // Draw all the children, leaving the drag view for last
                for (int i = pageCount - 1; i >= 0; i--) {
                    final View v = getPageAt(i);
                    if (v == mDragView) continue;
                    if (mForceDrawAllChildrenNextFrame ||
                               (leftScreen <= i && i <= rightScreen && shouldDrawChild(v))) {
                        drawChild(canvas, v, drawingTime);
                    }
                }
                // Draw the drag view on top (if there is one)
                if (mDragView != null) {
                    drawChild(canvas, mDragView, drawingTime);
                }

                mForceDrawAllChildrenNextFrame = false;
                canvas.restore();
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (getPageCount() > 0) {
            if (!mEdgeGlowLeft.isFinished()) {
                final int restoreCount = canvas.save();
                Rect display = mViewport;
                canvas.translate(display.left, display.top);
                canvas.rotate(270);

                getEdgeVerticalPostion(sTmpIntPoint);
                canvas.translate(display.top - sTmpIntPoint[1], 0);
                mEdgeGlowLeft.setSize(sTmpIntPoint[1] - sTmpIntPoint[0], display.width());
                if (mEdgeGlowLeft.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
            if (!mEdgeGlowRight.isFinished()) {
                final int restoreCount = canvas.save();
                Rect display = mViewport;
                canvas.translate(display.left + mPageScrolls[mIsRtl ? 0 : (getPageCount() - 1)], display.top);
                canvas.rotate(90);

                getEdgeVerticalPostion(sTmpIntPoint);
                canvas.translate(sTmpIntPoint[0] - display.top, -display.width());
                mEdgeGlowRight.setSize(sTmpIntPoint[1] - sTmpIntPoint[0], display.width());
                if (mEdgeGlowRight.draw(canvas)) {
                    postInvalidateOnAnimation();
                }
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    /**
     * Returns the top and bottom position for the edge effect.
     */
    protected abstract void getEdgeVerticalPostion(int[] pos);

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        int page = indexToPage(indexOfChild(child));
        if (page != mCurrentPage || !mScroller.isFinished()) {
            snapToPage(page);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int focusablePage;
        if (mNextPage != INVALID_PAGE) {
            focusablePage = mNextPage;
        } else {
            focusablePage = mCurrentPage;
        }
        View v = getPageAt(focusablePage);
        if (v != null) {
            return v.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        // XXX-RTL: This will be fixed in a future CL
        if (direction == View.FOCUS_LEFT) {
            if (getCurrentPage() > 0) {
                snapToPage(getCurrentPage() - 1);
                return true;
            }
        } else if (direction == View.FOCUS_RIGHT) {
            if (getCurrentPage() < getPageCount() - 1) {
                snapToPage(getCurrentPage() + 1);
                return true;
            }
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        // XXX-RTL: This will be fixed in a future CL
        if (mCurrentPage >= 0 && mCurrentPage < getPageCount()) {
            getPageAt(mCurrentPage).addFocusables(views, direction, focusableMode);
        }
        if (direction == View.FOCUS_LEFT) {
            if (mCurrentPage > 0) {
                getPageAt(mCurrentPage - 1).addFocusables(views, direction, focusableMode);
            }
        } else if (direction == View.FOCUS_RIGHT){
            if (mCurrentPage < getPageCount() - 1) {
                getPageAt(mCurrentPage + 1).addFocusables(views, direction, focusableMode);
            }
        }
    }

    /**
     * If one of our descendant views decides that it could be focused now, only
     * pass that along if it's on the current page.
     *
     * This happens when live folders requery, and if they're off page, they
     * end up calling requestFocus, which pulls it on page.
     */
    @Override
    public void focusableViewAvailable(View focused) {
        View current = getPageAt(mCurrentPage);
        View v = focused;
        while (true) {
            if (v == current) {
                super.focusableViewAvailable(focused);
                return;
            }
            if (v == this) {
                return;
            }
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                v = (View)v.getParent();
            } else {
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            // We need to make sure to cancel our long press if
            // a scrollable widget takes over touch events
            final View currentPage = getPageAt(mCurrentPage);
            currentPage.cancelLongPress();
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the previous page.
     */
    protected boolean hitsPreviousPage(float x, float y) {
        if (mIsRtl) {
            return (x > (getViewportOffsetX() + getViewportWidth() -
                    getPaddingRight() - mPageSpacing));
        }
        return (x < getViewportOffsetX() + getPaddingLeft() + mPageSpacing);
    }

    /**
     * Return true if a tap at (x, y) should trigger a flip to the next page.
     */
    protected boolean hitsNextPage(float x, float y) {
        if (mIsRtl) {
            return (x < getViewportOffsetX() + getPaddingLeft() + mPageSpacing);
        }
        return  (x > (getViewportOffsetX() + getViewportWidth() -
                getPaddingRight() - mPageSpacing));
    }

    /** Returns whether x and y originated within the buffered viewport */
    private boolean isTouchPointInViewportWithBuffer(int x, int y) {
        sTmpRect.set(mViewport.left - mViewport.width() / 2, mViewport.top,
                mViewport.right + mViewport.width() / 2, mViewport.bottom);
        return sTmpRect.contains(x, y);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        acquireVelocityTrackerAndAddMovement(ev);

        // 不处理没有页面的情况
        if (getChildCount() <= 0) return super.onInterceptTouchEvent(ev);

        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) &&
                (mTouchState == TOUCH_STATE_SCROLLING)) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */
                if (mActivePointerId != INVALID_POINTER) {
                    determineScrollingStart(ev);
                }
                // if mActivePointerId is INVALID_POINTER, then we must have missed an ACTION_DOWN
                // event. in that case, treat the first occurence of a move event as a ACTION_DOWN
                // i.e. fall through to the next case (don't break)
                // (We sometimes miss ACTION_DOWN events in Workspace because it ignores all events
                // while it's small- this was causing a crash before we checked for INVALID_POINTER)
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                // Remember location of down touch
                mDownMotionX = x;
                mDownMotionY = y;
                mDownScrollX = getScrollX();
                mLastMotionX = x;
                mLastMotionY = y;
                float[] p = mapPointFromViewToParent(this, x, y);
                mParentDownMotionX = p[0];
                mParentDownMotionY = p[1];
                mLastMotionXRemainder = 0;
                mTotalMotionX = 0;
                mActivePointerId = ev.getPointerId(0);

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                final int xDist = Math.abs(mScroller.getFinalX() - mScroller.getCurrX());
                final boolean finishedScrolling = (mScroller.isFinished() || xDist < mTouchSlop / 3);

                if (finishedScrolling) {
                    mTouchState = TOUCH_STATE_REST;
                    if (!mScroller.isFinished() && !mFreeScroll) {
                        setCurrentPage(getNextPage());
                        pageEndMoving();
                    }
                } else {
                    if (isTouchPointInViewportWithBuffer((int) mDownMotionX, (int) mDownMotionY)) {
                        mTouchState = TOUCH_STATE_SCROLLING;
                    } else {
                        mTouchState = TOUCH_STATE_REST;
                    }
                }

                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTouchState();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mTouchState != TOUCH_STATE_REST;
    }

    protected void determineScrollingStart(MotionEvent ev) {
        determineScrollingStart(ev, 1.0f);
    }

    /*
     * Determines if we should change the touch state to start scrolling after the
     * user moves their touch point too far.
     */
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        // Disallow scrolling if we don't have a valid pointer index
        final int pointerIndex = ev.findPointerIndex(mActivePointerId);
        if (pointerIndex == -1) return;

        // Disallow scrolling if we started the gesture from outside the viewport
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);
        if (!isTouchPointInViewportWithBuffer((int) x, (int) y)) return;

        final int xDiff = (int) Math.abs(x - mLastMotionX);

        final int touchSlop = Math.round(touchSlopScale * mTouchSlop);
        boolean xMoved = xDiff > touchSlop;

        if (xMoved) {
            // Scroll if the user moved far enough along the X axis
            mTouchState = TOUCH_STATE_SCROLLING;
            mTotalMotionX += Math.abs(mLastMotionX - x);
            mLastMotionX = x;
            mLastMotionXRemainder = 0;
            mTouchX = getViewportOffsetX() + getScrollX();
            mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
            onScrollInteractionBegin();
            pageBeginMoving();
        }
    }

    protected void cancelCurrentPageLongPress() {
        // Try canceling the long press. It could also have been scheduled
        // by a distant descendant, so use the mAllowLongPress flag to block
        // everything
        final View currentPage = getPageAt(mCurrentPage);
        if (currentPage != null) {
            currentPage.cancelLongPress();
        }
    }

    protected float getScrollProgress(int screenCenter, View v, int page) {
        final int halfScreenSize = getViewportWidth() / 2;

        int delta = screenCenter - (getScrollForPage(page) + halfScreenSize);
        int count = getChildCount();

        final int totalDistance;

        int adjacentPage = page + 1;
        if ((delta < 0 && !mIsRtl) || (delta > 0 && mIsRtl)) {
            adjacentPage = page - 1;
        }

        if (adjacentPage < 0 || adjacentPage > count - 1) {
            totalDistance = v.getMeasuredWidth() + mPageSpacing;
        } else {
            totalDistance = Math.abs(getScrollForPage(adjacentPage) - getScrollForPage(page));
        }

        float scrollProgress = delta / (totalDistance * 1.0f);
        scrollProgress = Math.min(scrollProgress, MAX_SCROLL_PROGRESS);
        scrollProgress = Math.max(scrollProgress, - MAX_SCROLL_PROGRESS);
        return scrollProgress;
    }

    public int getScrollForPage(int index) {
        if (mPageScrolls == null || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            return mPageScrolls[index];
        }
    }

    // While layout transitions are occurring, a child's position may stray from its baseline
    // position. This method returns the magnitude of this stray at any given time.
    public int getLayoutTransitionOffsetForPage(int index) {
        if (mPageScrolls == null || index >= mPageScrolls.length || index < 0) {
            return 0;
        } else {
            View child = getChildAt(index);

            int scrollOffset = 0;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (!lp.isFullScreenPage) {
                scrollOffset = mIsRtl ? getPaddingRight() : getPaddingLeft();
            }

            int baselineX = mPageScrolls[index] + scrollOffset + getViewportOffsetX();
            return (int) (child.getX() - baselineX);
        }
    }

    protected void dampedOverScroll(float amount) {
        int screenSize = getViewportWidth();
        float f = (amount / screenSize);
        if (f < 0) {
            mEdgeGlowLeft.onPull(-f);
        } else if (f > 0) {
            mEdgeGlowRight.onPull(f);
        } else {
            return;
        }
        invalidate();
    }

    protected void overScroll(float amount) {
        dampedOverScroll(amount);
    }

    public void enableFreeScroll() {
        setEnableFreeScroll(true);
    }

    public void disableFreeScroll() {
        setEnableFreeScroll(false);
    }

    void updateFreescrollBounds() {
        getFreeScrollPageRange(mTempVisiblePagesRange);
        if (mIsRtl) {
            mFreeScrollMinScrollX = getScrollForPage(mTempVisiblePagesRange[1]);
            mFreeScrollMaxScrollX = getScrollForPage(mTempVisiblePagesRange[0]);
        } else {
            mFreeScrollMinScrollX = getScrollForPage(mTempVisiblePagesRange[0]);
            mFreeScrollMaxScrollX = getScrollForPage(mTempVisiblePagesRange[1]);
        }
    }

    private void setEnableFreeScroll(boolean freeScroll) {
        mFreeScroll = freeScroll;

        if (mFreeScroll) {
            updateFreescrollBounds();
            getFreeScrollPageRange(mTempVisiblePagesRange);
            if (getCurrentPage() < mTempVisiblePagesRange[0]) {
                setCurrentPage(mTempVisiblePagesRange[0]);
            } else if (getCurrentPage() > mTempVisiblePagesRange[1]) {
                setCurrentPage(mTempVisiblePagesRange[1]);
            }
        }

        setEnableOverscroll(!freeScroll);
    }

    protected void setEnableOverscroll(boolean enable) {
        mAllowOverScroll = enable;
    }

    private int getNearestHoverOverPageIndex() {
        if (mDragView != null) {
            int dragX = (int) (mDragView.getLeft() + (mDragView.getMeasuredWidth() / 2)
                    + mDragView.getTranslationX());
            getFreeScrollPageRange(mTempVisiblePagesRange);
            int minDistance = Integer.MAX_VALUE;
            int minIndex = indexOfChild(mDragView);
            for (int i = mTempVisiblePagesRange[0]; i <= mTempVisiblePagesRange[1]; i++) {
                View page = getPageAt(i);
                int pageX = (int) (page.getLeft() + page.getMeasuredWidth() / 2);
                int d = Math.abs(dragX - pageX);
                if (d < minDistance) {
                    minIndex = i;
                    minDistance = d;
                }
            }
            return minIndex;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        super.onTouchEvent(ev);

        // 不处理没有页面的情况
        if (getChildCount() <= 0) return super.onTouchEvent(ev);

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            //如果滑动没有完成, 则终止滑动动画
            if (!mScroller.isFinished()) {
                abortScrollerAnimation(false);
            }

            //记得运动事件开始的地方
            mDownMotionX = mLastMotionX = ev.getX();
            mDownMotionY = mLastMotionY = ev.getY();
            mDownScrollX = getScrollX();
            float[] p = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
            mParentDownMotionX = p[0];
            mParentDownMotionY = p[1];
            mLastMotionXRemainder = 0;
            mTotalMotionX = 0;
            mActivePointerId = ev.getPointerId(0);

            //里面的方法子类实现的
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                onScrollInteractionBegin();
                pageBeginMoving();
            }
            break;

        case MotionEvent.ACTION_MOVE:
            /** ACTION_MOVE--------------------------滚动状态-------------------------- */
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                //获取有效的手指数
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) return true;

                final float x = ev.getX(pointerIndex);
                final float deltaX = mLastMotionX + mLastMotionXRemainder - x;

                mTotalMotionX += Math.abs(deltaX);

                //根据滑动距离调用scrollBy方法滑动workspace
                if (Math.abs(deltaX) >= 1.0f) {
                    mTouchX += deltaX;
                    mSmoothingTime = System.nanoTime() / NANOTIME_DIV;
                    scrollBy((int) deltaX, 0);
                    mLastMotionX = x;
                    mLastMotionXRemainder = deltaX - (int) deltaX;
                } else {
                    awakenScrollBars();
                }
            }
            /** ACTION_MOVE--------------------------排序状态-------------------------- */
            else if (mTouchState == TOUCH_STATE_REORDERING) {
                //记录移动过程中的位置
                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();

                float[] pt = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
                mParentDownMotionX = pt[0];
                mParentDownMotionY = pt[1];

                //更新你正在拖动排序的View的位置
                updateDragViewTranslationDuringDrag();

                //查找距离手指最近的CellLayout的Index
                final int dragViewIndex = indexOfChild(mDragView);

                if (DEBUG) Log.d(TAG, "mLastMotionX: " + mLastMotionX);
                if (DEBUG) Log.d(TAG, "mLastMotionY: " + mLastMotionY);
                if (DEBUG) Log.d(TAG, "mParentDownMotionX: " + mParentDownMotionX);
                if (DEBUG) Log.d(TAG, "mParentDownMotionY: " + mParentDownMotionY);

                //查找手指移动到的位置所在的CellLayoutIndex,
                //这个CellLayout是拖动过程中手指到达的位置处的CellLayout，没用动的
                final int pageUnderPointIndex = getNearestHoverOverPageIndex();
                if (pageUnderPointIndex > -1 && pageUnderPointIndex != indexOfChild(mDragView)) {
                    mTempVisiblePagesRange[0] = 0;
                    mTempVisiblePagesRange[1] = getPageCount() - 1;
                    getFreeScrollPageRange(mTempVisiblePagesRange);
                    if (mTempVisiblePagesRange[0] <= pageUnderPointIndex &&
                            pageUnderPointIndex <= mTempVisiblePagesRange[1] &&
                            pageUnderPointIndex != mSidePageHoverIndex && mScroller.isFinished()) {
                        mSidePageHoverIndex = pageUnderPointIndex;
                        mSidePageHoverRunnable = new Runnable() {
                            @Override
                            public void run() {
                                // 在交换位置前先滑动到手指所在的那个CellLayout位置
                                snapToPage(pageUnderPointIndex);
                                // 获取CellLayout的变化值，如果拖动的view的index小于手指位置处未动的view的index，
                                // 则需要-1，也就是向前移动，反之向后移动，index+1
                                int shiftDelta = (dragViewIndex < pageUnderPointIndex) ? -1 : 1;
                                int lowerIndex = (dragViewIndex < pageUnderPointIndex) ?
                                        dragViewIndex + 1 : pageUnderPointIndex;
                                int upperIndex = (dragViewIndex > pageUnderPointIndex) ?
                                        dragViewIndex - 1 : pageUnderPointIndex;

                                //shiftDelta, lowerIndex, upperIndex这三个值就是确定交换的位置，
                                // 也就是如果从前向后拖动CellLayout，那么被拖动的Index要变大，反之变小，
                                // 后两个参数来计算拖动CellLayout的跨度，如果向后拖动，
                                // 那么中间被跨过的几个Celllayout就要顺序向前移动，反之向后移动
                                // for循环就是移动的过程
                                for (int i = lowerIndex; i <= upperIndex; ++i) {
                                    View v = getChildAt(i);
                                    // dragViewIndex < pageUnderPointIndex, so after we remove the
                                    // drag view all subsequent views to pageUnderPointIndex will
                                    // shift down.
                                    int oldX = getViewportOffsetX() + getChildOffset(i);
                                    int newX = getViewportOffsetX() + getChildOffset(i + shiftDelta);

                                    // Animate the view translation from its old position to its new
                                    // position
                                    AnimatorSet anim = (AnimatorSet) v.getTag(ANIM_TAG_KEY);
                                    if (anim != null) {
                                        anim.cancel();
                                    }

                                    v.setTranslationX(oldX - newX);
                                    anim = new AnimatorSet();
                                    anim.setDuration(REORDERING_REORDER_REPOSITION_DURATION);
                                    anim.playTogether(
                                            ObjectAnimator.ofFloat(v, "translationX", 0f));
                                    anim.start();
                                    v.setTag(anim);
                                }
                                //移除拖动的View
                                removeView(mDragView);
                                //添加被拖动view到新的位置
                                addView(mDragView, pageUnderPointIndex);
                                mSidePageHoverIndex = -1;
                                if (mPageIndicator != null) {
                                    mPageIndicator.setActiveMarker(getNextPage());
                                }
                            }
                        };

                        postDelayed(mSidePageHoverRunnable, REORDERING_SIDE_PAGE_HOVER_TIMEOUT);
                    }
                } else {
                    removeCallbacks(mSidePageHoverRunnable);
                    mSidePageHoverIndex = -1;
                }
            }
            /** ACTION_MOVE--------------------------其他状态（向前、向后）-------------------------- */
            else {
                //执行滑动开启
                determineScrollingStart(ev);
            }
            break;
        case MotionEvent.ACTION_UP:
            /** ACTION_UP--------------------------滑动状态-------------------------- */
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                final int activePointerId = mActivePointerId;
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                final float x = ev.getX(pointerIndex);
                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                final int deltaX = (int) (x - mDownMotionX);
                final int pageWidth = getPageAt(mCurrentPage).getMeasuredWidth();

                //是否是有效事件，也就是滑动位置是否超过了pagedView的40%
                boolean isSignificantMove = Math.abs(deltaX) > pageWidth *
                        SIGNIFICANT_MOVE_THRESHOLD;

                mTotalMotionX += Math.abs(mLastMotionX + mLastMotionXRemainder - x);

                boolean isFling = mTotalMotionX > MIN_LENGTH_FOR_FLING &&
                        Math.abs(velocityX) > mFlingThresholdVelocity;

                /** 在左右滑动时，有个有效值，也就是手指滑动距离超过了该值，
                 * 则认为是有效的，到你超过这个值然后抬起手指，则认为你滑动了一屏，
                 * 剩下的距离根据惯性自动完成，如果你滑动没有超过这个值，
                 * 则认为你切换屏幕是无效的，抬起手指后屏幕会返回到初始的屏幕位置 */
                if (!mFreeScroll) {
                    // In the case that the page is moved far to one direction and then is flung
                    // in the opposite direction, we use a threshold to determine whether we should
                    // just return to the starting page, or if we should skip one further.
                    boolean returnToOriginalPage = false;
                    if (Math.abs(deltaX) > pageWidth * RETURN_TO_ORIGINAL_PAGE_THRESHOLD &&
                            Math.signum(velocityX) != Math.signum(deltaX) && isFling) {
                        returnToOriginalPage = true;
                    }

                    int finalPage;
                    // We give flings precedence over large moves, which is why we short-circuit our
                    // test for a large move if a fling has been registered. That is, a large
                    // move to the left and fling to the right will register as a fling to the right.
                    boolean isDeltaXLeft = mIsRtl ? deltaX > 0 : deltaX < 0;
                    boolean isVelocityXLeft = mIsRtl ? velocityX > 0 : velocityX < 0;
                    if (((isSignificantMove && !isDeltaXLeft && !isFling) ||
                            (isFling && !isVelocityXLeft)) && mCurrentPage > 0) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage - 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else if (((isSignificantMove && isDeltaXLeft && !isFling) ||
                            (isFling && isVelocityXLeft)) &&
                            mCurrentPage < getChildCount() - 1) {
                        finalPage = returnToOriginalPage ? mCurrentPage : mCurrentPage + 1;
                        snapToPageWithVelocity(finalPage, velocityX);
                    } else {
                        snapToDestination();
                    }
                } else {
                    if (!mScroller.isFinished()) {
                        abortScrollerAnimation(true);
                    }

                    float scaleX = getScaleX();
                    int vX = (int) (-velocityX * scaleX);
                    int initialScrollX = (int) (getScrollX() * scaleX);

                    mScroller.setInterpolator(mDefaultInterpolator);
                    mScroller.fling(initialScrollX,
                            getScrollY(), vX, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0);
                    invalidate();
                }
                onScrollInteractionEnd();
            }
            /** ACTION_UP--------------------------向前翻页状态-------------------------- */
            else if (mTouchState == TOUCH_STATE_PREV_PAGE) {
                //如果不是第一屏，滑动到前一屏
                int nextPage = Math.max(0, mCurrentPage - 1);
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            }
            /** ACTION_UP--------------------------向后翻页状态-------------------------- */
            else if (mTouchState == TOUCH_STATE_NEXT_PAGE) {
                //不是最后一屏，滑动到下一屏
                int nextPage = Math.min(getChildCount() - 1, mCurrentPage + 1);
                if (nextPage != mCurrentPage) {
                    snapToPage(nextPage);
                } else {
                    snapToDestination();
                }
            }
            /** ACTION_UP--------------------------排序状态-------------------------- */
            else if (mTouchState == TOUCH_STATE_REORDERING) {
                //调用updateDragViewTranslationDuringDrag方法
                // 移动拖拽的View到相应的位置

                // 更新最后一个运动位置
                mLastMotionX = ev.getX();
                mLastMotionY = ev.getY();

                // Update the parent down so that our zoom animations take this new movement into
                // account
                float[] pt = mapPointFromViewToParent(this, mLastMotionX, mLastMotionY);
                mParentDownMotionX = pt[0];
                mParentDownMotionY = pt[1];
                updateDragViewTranslationDuringDrag();
            } else {
                if (!mCancelTap) {
                    onUnhandledTap(ev);
                }
            }

            // Remove the callback to wait for the side page hover timeout
            removeCallbacks(mSidePageHoverRunnable);
            // 恢复一些触摸状态
            resetTouchState();
            break;

        case MotionEvent.ACTION_CANCEL:
            if (mTouchState == TOUCH_STATE_SCROLLING) {
                snapToDestination();
            }
            resetTouchState();
            break;

        case MotionEvent.ACTION_POINTER_UP:
            onSecondaryPointerUp(ev);
            releaseVelocityTracker();
            break;
        }

        return true;
    }

    private void resetTouchState() {
        releaseVelocityTracker();
        endReordering();
        mCancelTap = false;
        mTouchState = TOUCH_STATE_REST;
        mActivePointerId = INVALID_POINTER;
        mEdgeGlowLeft.onRelease();
        mEdgeGlowRight.onRelease();
    }

    protected void onUnhandledTap(MotionEvent ev) {
        ((Launcher) getContext()).onClick(this);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL: {
                    // Handle mouse (or ext. device) by shifting the page depending on the scroll
                    final float vscroll;
                    final float hscroll;
                    if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                        vscroll = 0;
                        hscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    } else {
                        vscroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        hscroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                    }
                    if (hscroll != 0 || vscroll != 0) {
                        boolean isForwardScroll = mIsRtl ? (hscroll < 0 || vscroll < 0)
                                                         : (hscroll > 0 || vscroll > 0);
                        if (isForwardScroll) {
                            scrollRight();
                        } else {
                            scrollLeft();
                        }
                        return true;
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            // TODO: Make this decision more intelligent.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = mDownMotionX = ev.getX(newPointerIndex);
            mLastMotionY = ev.getY(newPointerIndex);
            mLastMotionXRemainder = 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int page = indexToPage(indexOfChild(child));
        if (page >= 0 && page != getCurrentPage() && !isInTouchMode()) {
            snapToPage(page);
        }
    }

    int getPageNearestToCenterOfScreen() {
        int minDistanceFromScreenCenter = Integer.MAX_VALUE;
        int minDistanceFromScreenCenterIndex = -1;
        int screenCenter = getViewportOffsetX() + getScrollX() + (getViewportWidth() / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View layout = (View) getPageAt(i);
            int childWidth = layout.getMeasuredWidth();
            int halfChildWidth = (childWidth / 2);
            int childCenter = getViewportOffsetX() + getChildOffset(i) + halfChildWidth;
            int distanceFromScreenCenter = Math.abs(childCenter - screenCenter);
            if (distanceFromScreenCenter < minDistanceFromScreenCenter) {
                minDistanceFromScreenCenter = distanceFromScreenCenter;
                minDistanceFromScreenCenterIndex = i;
            }
        }
        return minDistanceFromScreenCenterIndex;
    }

    protected void snapToDestination() {
        snapToPage(getPageNearestToCenterOfScreen(), PAGE_SNAP_ANIMATION_DURATION);
    }

    private static class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            return t*t*t*t*t + 1;
        }
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    protected void snapToPageWithVelocity(int whichPage, int velocity) {
        whichPage = validateNewPage(whichPage);
        int halfScreenSize = getViewportWidth() / 2;

        final int newX = getScrollForPage(whichPage);
        int delta = newX - getScrollX();
        int duration = 0;

        if (Math.abs(velocity) < mMinFlingVelocity) {
            // If the velocity is low enough, then treat this more as an automatic page advance
            // as opposed to an apparent physical response to flinging
            snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
            return;
        }

        // Here we compute a "distance" that will be used in the computation of the overall
        // snap duration. This is a function of the actual distance that needs to be traveled;
        // we keep this value close to half screen size in order to reduce the variance in snap
        // duration as a function of the distance the page needs to travel.
        float distanceRatio = Math.min(1f, 1.0f * Math.abs(delta) / (2 * halfScreenSize));
        float distance = halfScreenSize + halfScreenSize *
                distanceInfluenceForSnapDuration(distanceRatio);

        velocity = Math.abs(velocity);
        velocity = Math.max(mMinSnapVelocity, velocity);

        // we want the page's snap velocity to approximately match the velocity at which the
        // user flings, so we scale the duration by a value near to the derivative of the scroll
        // interpolator at zero, ie. 5. We use 4 to make it a little slower.
        duration = 4 * Math.round(1000 * Math.abs(distance / velocity));

        snapToPage(whichPage, delta, duration);
    }

    //scrollBy方法：这个方法其实很简单最终调用的是scrollTo方法，
    // 也就是移动到相应的位置，最后调用View的scrollTo方法；
    //snapToPage方法：这个方法最终调用mScroller.startScroll(),
    // 计算出最终位置，然后滑动到相应位置即可。
    public void snapToPage(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION);
    }

    protected void snapToPageImmediately(int whichPage) {
        snapToPage(whichPage, PAGE_SNAP_ANIMATION_DURATION, true, null);
    }

    protected void snapToPage(int whichPage, int duration) {
        snapToPage(whichPage, duration, false, null);
    }

    protected void snapToPage(int whichPage, int duration, TimeInterpolator interpolator) {
        snapToPage(whichPage, duration, false, interpolator);
    }

    protected void snapToPage(int whichPage, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        whichPage = validateNewPage(whichPage);

        int newX = getScrollForPage(whichPage);
        final int delta = newX - getScrollX();
        snapToPage(whichPage, delta, duration, immediate, interpolator);
    }

    protected void snapToPage(int whichPage, int delta, int duration) {
        snapToPage(whichPage, delta, duration, false, null);
    }

    protected void snapToPage(int whichPage, int delta, int duration, boolean immediate,
            TimeInterpolator interpolator) {
        whichPage = validateNewPage(whichPage);

        mNextPage = whichPage;
        View focusedChild = getFocusedChild();
        if (focusedChild != null && whichPage != mCurrentPage &&
                focusedChild == getPageAt(mCurrentPage)) {
            focusedChild.clearFocus();
        }

        pageBeginMoving();
        awakenScrollBars(duration);
        if (immediate) {
            duration = 0;
        } else if (duration == 0) {
            duration = Math.abs(delta);
        }

        if (!mScroller.isFinished()) {
            abortScrollerAnimation(false);
        }

        if (interpolator != null) {
            mScroller.setInterpolator(interpolator);
        } else {
            mScroller.setInterpolator(mDefaultInterpolator);
        }

        mScroller.startScroll(getScrollX(), 0, delta, 0, duration);

        updatePageIndicator();

        // Trigger a compute() to finish switching pages if necessary
        if (immediate) {
            computeScroll();
        }

        mForceScreenScrolled = true;
        invalidate();
    }

    public void scrollLeft() {
        if (getNextPage() > 0) snapToPage(getNextPage() - 1);
    }

    public void scrollRight() {
        if (getNextPage() < getChildCount() -1) snapToPage(getNextPage() + 1);
    }

    public int getPageForView(View v) {
        int result = -1;
        if (v != null) {
            ViewParent vp = v.getParent();
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                if (vp == getPageAt(i)) {
                    return i;
                }
            }
        }
        return result;
    }

    @Override
    public boolean performLongClick() {
        mCancelTap = true;
        return super.performLongClick();
    }

    public static class SavedState extends BaseSavedState {
        int currentPage = -1;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Thunk SavedState(Parcel in) {
            super(in);
            currentPage = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentPage);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    // Animate the drag view back to the original position
    private void animateDragViewToOriginalPosition() {
        if (mDragView != null) {
            AnimatorSet anim = new AnimatorSet();
            anim.setDuration(REORDERING_DROP_REPOSITION_DURATION);
            anim.playTogether(
                    ObjectAnimator.ofFloat(mDragView, "translationX", 0f),
                    ObjectAnimator.ofFloat(mDragView, "translationY", 0f),
                    ObjectAnimator.ofFloat(mDragView, "scaleX", 1f),
                    ObjectAnimator.ofFloat(mDragView, "scaleY", 1f));
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onPostReorderingAnimationCompleted();
                }
            });
            anim.start();
        }
    }

    public void onStartReordering() {
        // Set the touch state to reordering (allows snapping to pages, dragging a child, etc.)
        mTouchState = TOUCH_STATE_REORDERING;
        mIsReordering = true;

        // We must invalidate to trigger a redraw to update the layers such that the drag view
        // is always drawn on top
        invalidate();
    }

    @Thunk void onPostReorderingAnimationCompleted() {
        // Trigger the callback when reordering has settled
        --mPostReorderingPreZoomInRemainingAnimationCount;
        if (mPostReorderingPreZoomInRunnable != null &&
                mPostReorderingPreZoomInRemainingAnimationCount == 0) {
            mPostReorderingPreZoomInRunnable.run();
            mPostReorderingPreZoomInRunnable = null;
        }
    }

    public void onEndReordering() {
        mIsReordering = false;
    }

    public boolean startReordering(View v) {
        int dragViewIndex = indexOfChild(v);

        if (mTouchState != TOUCH_STATE_REST || dragViewIndex == -1) return false;

        mTempVisiblePagesRange[0] = 0;
        mTempVisiblePagesRange[1] = getPageCount() - 1;
        getFreeScrollPageRange(mTempVisiblePagesRange);
        mReorderingStarted = true;

        // Check if we are within the reordering range
        if (mTempVisiblePagesRange[0] <= dragViewIndex &&
            dragViewIndex <= mTempVisiblePagesRange[1]) {
            // Find the drag view under the pointer
            mDragView = getChildAt(dragViewIndex);
            mDragView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start();
            mDragViewBaselineLeft = mDragView.getLeft();
            snapToPage(getPageNearestToCenterOfScreen());
            disableFreeScroll();
            onStartReordering();
            return true;
        }
        return false;
    }

    boolean isReordering(boolean testTouchState) {
        boolean state = mIsReordering;
        if (testTouchState) {
            state &= (mTouchState == TOUCH_STATE_REORDERING);
        }
        return state;
    }
    void endReordering() {
        // For simplicity, we call endReordering sometimes even if reordering was never started.
        // In that case, we don't want to do anything.
        if (!mReorderingStarted) return;
        mReorderingStarted = false;

        // If we haven't flung-to-delete the current child, then we just animate the drag view
        // back into position
        final Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                onEndReordering();
            }
        };

        mPostReorderingPreZoomInRunnable = new Runnable() {
            public void run() {
                onCompleteRunnable.run();
                enableFreeScroll();
            };
        };

        mPostReorderingPreZoomInRemainingAnimationCount =
                NUM_ANIMATIONS_RUNNING_BEFORE_ZOOM_OUT;
        // Snap to the current page
        snapToPage(indexOfChild(mDragView), 0);
        // Animate the drag view back to the front position
        animateDragViewToOriginalPosition();
    }

    private static final int ANIM_TAG_KEY = 100;

    /* Accessibility */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(getPageCount() > 1);
        if (getCurrentPage() < getPageCount() - 1) {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        if (getCurrentPage() > 0) {
            info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
        info.setClassName(getClass().getName());

        // Accessibility-wise, PagedView doesn't support long click, so disabling it.
        // Besides disabling the accessibility long-click, this also prevents this view from getting
        // accessibility focus.
        info.setLongClickable(false);
        if (Utilities.ATLEAST_LOLLIPOP) {
            info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
        }
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // Don't let the view send real scroll events.
        if (eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            super.sendAccessibilityEvent(eventType);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setScrollable(getPageCount() > 1);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                if (getCurrentPage() < getPageCount() - 1) {
                    scrollRight();
                    return true;
                }
            } break;
            case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                if (getCurrentPage() > 0) {
                    scrollLeft();
                    return true;
                }
            } break;
        }
        return false;
    }

    protected String getCurrentPageDescription() {
        return String.format(getContext().getString(R.string.default_scroll_format),
                getNextPage() + 1, getChildCount());
    }

    @Override
    public boolean onHoverEvent(android.view.MotionEvent event) {
        return true;
    }

    protected void onScrollInteractionBegin() {}

    protected void onScrollInteractionEnd() {}
}
