/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.design.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.multioperationedittext.R;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.Space;
import android.support.v4.widget.TextViewCompat;
import android.support.v4.widget.ViewGroupUtils;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.TintTypedArray;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * copy from android.support.design
 * add the textView and CheckableImageButton to support additional operation
 */
public class MultiOperationInputLayout extends LinearLayout {

    private static final int ANIMATION_DURATION = 200;
    private static final int INVALID_MAX_LENGTH = -1;

    private final FrameLayout mInputFrame;
    EditText mEditText;

    private boolean mHintEnabled;
    private CharSequence mHint;

    private Paint mTmpPaint;
    private final Rect mTmpRect = new Rect();

    private LinearLayout mIndicatorArea;
    private int mIndicatorsAdded;

    private Typeface mTypeface;

    private boolean mErrorEnabled;
    TextView mErrorView;
    private int mErrorTextAppearance;
    private boolean mErrorShown;
    private CharSequence mError;

    boolean mCounterEnabled;
    private TextView mCounterView;
    private int mCounterMaxLength;
    private int mCounterTextAppearance;
    private int mCounterOverflowTextAppearance;
    private boolean mCounterOverflowed;

    private TextView mOperationTextView;
    private int mOperationTextViewSize = 15;
    private ColorStateList mOperationTextViewColor;
    private CharSequence mOperationText;
    private OnClickListener mOperationTextViewOnclickListener;

    private boolean mOperationToggleChecked;
    private Drawable mOperationToggleDrawable;
    private CharSequence mOperationToggleContentDesc;
    private CheckableImageButton mOperationToggleView;
    private Drawable mOperationToggleDummyDrawable;
    private Drawable mOriginalEditTextEndDrawable;

    private boolean mOperationToggleApplyTint;
    private ColorStateList mOperationToggleTintList;
    private boolean mHasOperationToggleTintList;
    private PorterDuff.Mode mOperationToggleTintMode;
    private boolean mHasOperationToggleTintMode;
    private OnClickListener mMultiOperationToggleOnclickListener;

    private ColorStateList mDefaultTextColor;
    private ColorStateList mFocusedTextColor;
    private ColorStateList mErrorTextColor;
    private boolean showErrorWithoutErrorText;

    private boolean stickyEditTextBackgroundColor;

    // Only used for testing
    private boolean mHintExpanded;

    final CollapsingTextHelper mCollapsingTextHelper = new CollapsingTextHelper(this);

    private boolean mHintAnimationEnabled;
    private ValueAnimator mAnimator;

    private boolean mHasReconstructedEditTextBackground;
    private boolean mInDrawableStateChanged;

    private boolean mRestoringSavedState;
    private int mOperationType;
    private final static int OPERATION_TYPE_EDIT_TEXT_ONLY = 1;
    private final static int OPERATION_TYPE_EDIT_TEXT_WITH_TEXT = 2;
    private final static int OPERATION_TYPE_EDIT_TEXT_WITH_TOGGLE = 3;
    private int mOperationToggleType;
    private final static int OPERATION_TOGGLE_TYPE_PASSWORD = 1;
    private final static int OPERATION_TOGGLE_TYPE_OTHER = 2;

    public MultiOperationInputLayout(Context context) {
        this(context, null);
    }

    public MultiOperationInputLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressLint("RestrictedApi")
    public MultiOperationInputLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        // Can't call through to super(Context, AttributeSet, int) since it doesn't exist on API 10
        super(context, attrs);

        ThemeUtils.checkAppCompatTheme(context);

        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setAddStatesFromChildren(true);

        mInputFrame = new FrameLayout(context);
        mInputFrame.setAddStatesFromChildren(true);
        addView(mInputFrame);

        mCollapsingTextHelper.setTextSizeInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
        mCollapsingTextHelper.setPositionInterpolator(new AccelerateInterpolator());
        mCollapsingTextHelper.setCollapsedTextGravity(Gravity.TOP | GravityCompat.START);

        @SuppressLint("RestrictedApi") final TintTypedArray a = TintTypedArray.obtainStyledAttributes(context, attrs,
                R.styleable.MultiOperationInputLayout, defStyleAttr, R.style.MultiOperationInputLayout);
        mHintEnabled = a.getBoolean(R.styleable.MultiOperationInputLayout_hintEnabled, true);
        setHint(a.getText(R.styleable.MultiOperationInputLayout_android_hint));
        mHintAnimationEnabled = a.getBoolean(
                R.styleable.MultiOperationInputLayout_hintAnimationEnabled, true);

        if (a.hasValue(R.styleable.MultiOperationInputLayout_android_textColorHint)) {
            mDefaultTextColor = mFocusedTextColor =
                    a.getColorStateList(R.styleable.MultiOperationInputLayout_android_textColorHint);
        }

        final int hintAppearance = a.getResourceId(
                R.styleable.MultiOperationInputLayout_hintTextAppearance, -1);
        if (hintAppearance != -1) {
            setHintTextAppearance(
                    a.getResourceId(R.styleable.MultiOperationInputLayout_hintTextAppearance, 0));
        }

        mOperationToggleApplyTint = a.getBoolean(R.styleable.MultiOperationInputLayout_operationToggleApplyTint, false);
        mErrorTextAppearance = a.getResourceId(R.styleable.MultiOperationInputLayout_errorTextAppearance, 0);
        final boolean errorEnabled = a.getBoolean(R.styleable.MultiOperationInputLayout_errorEnabled, false);

        final boolean counterEnabled = a.getBoolean(
                R.styleable.MultiOperationInputLayout_counterEnabled, false);
        setCounterMaxLength(
                a.getInt(R.styleable.MultiOperationInputLayout_counterMaxLength, INVALID_MAX_LENGTH));
        mCounterTextAppearance = a.getResourceId(
                R.styleable.MultiOperationInputLayout_counterTextAppearance, 0);
        mCounterOverflowTextAppearance = a.getResourceId(
                R.styleable.MultiOperationInputLayout_counterOverflowTextAppearance, 0);

        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationToggleDrawable)) {
            mOperationToggleDrawable = a.getDrawable(R.styleable.MultiOperationInputLayout_operationToggleDrawable);
        } else {
            mOperationToggleDrawable = getResources().getDrawable(R.drawable.design_password_eye_icon);
        }
        mOperationToggleDrawable = a.getDrawable(R.styleable.MultiOperationInputLayout_operationToggleDrawable);
        mOperationToggleContentDesc = a.getText(
                R.styleable.MultiOperationInputLayout_operationToggleContentDescription);
        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationToggleTint)) {
            mHasOperationToggleTintList = true;
            mOperationToggleTintList = a.getColorStateList(
                    R.styleable.MultiOperationInputLayout_operationToggleTint);
        }
        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationToggleTintMode)) {
            mHasOperationToggleTintMode = true;
            mOperationToggleTintMode = ViewUtils.parseTintMode(
                    a.getInt(R.styleable.MultiOperationInputLayout_operationToggleTintMode, -1), null);
        }

        mOperationType = a.getInt(R.styleable.MultiOperationInputLayout_operationType, 1);

        mOperationToggleType = a.getInt(R.styleable.MultiOperationInputLayout_operationToggleType, 2);

        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationErrorColor)) {
            mErrorTextColor = a.getColorStateList(R.styleable.MultiOperationInputLayout_operationErrorColor);
        } else {
            mErrorTextColor = mDefaultTextColor;
        }

        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationTextColor)) {
            mOperationTextViewColor = a.getColorStateList(R.styleable.MultiOperationInputLayout_operationTextColor);
        } else {
            mOperationTextViewColor = mDefaultTextColor;
        }

        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationTextSize)) {
            mOperationTextViewSize = a.getInteger(R.styleable.MultiOperationInputLayout_operationTextSize, mOperationTextViewSize);
        }

        if (a.hasValue(R.styleable.MultiOperationInputLayout_operationTextString)) {
            mOperationText = a.getText(R.styleable.MultiOperationInputLayout_operationTextString);
        } else {
            mOperationText = "";
        }

        mOperationToggleChecked = a.getBoolean(R.styleable.MultiOperationInputLayout_operationToggleChecked, false);
        stickyEditTextBackgroundColor = a.getBoolean(R.styleable.MultiOperationInputLayout_useStickyBackground, false);

        a.recycle();

        setErrorEnabled(errorEnabled);
        setCounterEnabled(counterEnabled);
        applyOperationToggleTint();

        if (ViewCompat.getImportantForAccessibility(this)
                == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            // Make sure we're important for accessibility if we haven't been explicitly not
            ViewCompat.setImportantForAccessibility(this,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        ViewCompat.setAccessibilityDelegate(this, new TextInputAccessibilityDelegate());
    }

    @Override
    public void addView(View child, int index, final ViewGroup.LayoutParams params) {
        if (child instanceof EditText) {
            // Make sure that the EditText is vertically at the bottom, so that it sits on the
            // EditText's underline
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(params);
            flp.gravity = Gravity.CENTER_VERTICAL | (flp.gravity & ~Gravity.VERTICAL_GRAVITY_MASK);
            mInputFrame.addView(child, flp);

            // Now use the EditText's LayoutParams as our own and update them to make enough space
            // for the label
            mInputFrame.setLayoutParams(params);
            updateInputLayoutMargins();

            setEditText((EditText) child);
        } else {
            // Carry on adding the View...
            super.addView(child, index, params);
        }
    }

    /**
     * Set the typeface to use for the hint and any label views (such as counter and error views).
     *
     * @param typeface typeface to use, or {@code null} to use the default.
     */
    public void setTypeface(@Nullable Typeface typeface) {
        if (typeface != mTypeface) {
            mTypeface = typeface;

            mCollapsingTextHelper.setTypefaces(typeface);
            if (mCounterView != null) {
                mCounterView.setTypeface(typeface);
            }
            if (mErrorView != null) {
                mErrorView.setTypeface(typeface);
            }
        }
    }

    /**
     * Returns the typeface used for the hint and any label views (such as counter and error views).
     */
    @NonNull
    public Typeface getTypeface() {
        return mTypeface;
    }

    private void setEditText(EditText editText) {
        // If we already have an EditText, throw an exception
        if (mEditText != null) {
            throw new IllegalArgumentException("We already have an EditText, can only have one");
        }

        mEditText = editText;

        final boolean hasPasswordTransformation = hasPasswordTransformation();

        // Use the EditText's typeface, and it's text size for our expanded text
        if (!hasPasswordTransformation) {
            // We don't want a monospace font just because we have a password field
            mCollapsingTextHelper.setTypefaces(mEditText.getTypeface());
        }
        mCollapsingTextHelper.setExpandedTextSize(mEditText.getTextSize());

        final int editTextGravity = mEditText.getGravity();
        mCollapsingTextHelper.setCollapsedTextGravity(
                Gravity.TOP | (editTextGravity & ~Gravity.VERTICAL_GRAVITY_MASK));
        mCollapsingTextHelper.setExpandedTextGravity(editTextGravity);

        // Add a TextWatcher so that we know when the text input has changed
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateLabelState(!mRestoringSavedState);
                if (mCounterEnabled) {
                    updateCounter(s.length());
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // Use the EditText's hint colors if we don't have one set
        if (mDefaultTextColor == null) {
            mDefaultTextColor = mEditText.getHintTextColors();
        }

        // If we do not have a valid hint, try and retrieve it from the EditText, if enabled
        if (mHintEnabled && TextUtils.isEmpty(mHint)) {
            setHint(mEditText.getHint());
            // Clear the EditText's hint as we will display it ourselves
            mEditText.setHint(null);
        }

        if (mCounterView != null) {
            updateCounter(mEditText.getText().length());
        }

        if (mIndicatorArea != null) {
            adjustIndicatorPadding();
        }

        if (isOperationToggleVisible()) {
            updateOperationToggleView();
        } else {
            updateOperationTextView();
        }


        // Update the label visibility with no animation, but force a state change
        updateLabelState(false, true);
    }

    private void updateInputLayoutMargins() {
        // Create/update the LayoutParams so that we can add enough top margin
        // to the EditText so make room for the label
        final LayoutParams lp = (LayoutParams) mInputFrame.getLayoutParams();
        final int newTopMargin;

        if (mHintEnabled) {
            if (mTmpPaint == null) {
                mTmpPaint = new Paint();
            }
            mTmpPaint.setTypeface(mCollapsingTextHelper.getCollapsedTypeface());
            mTmpPaint.setTextSize(mCollapsingTextHelper.getCollapsedTextSize());
            newTopMargin = (int) -mTmpPaint.ascent();
        } else {
            newTopMargin = 0;
        }

        if (newTopMargin != lp.topMargin) {
            lp.topMargin = newTopMargin;
            mInputFrame.requestLayout();
        }
    }

    void updateLabelState(boolean animate) {
        updateLabelState(animate, false);
    }

    /**
     * 是否显示错误提示
     * show the error hint without using the default textView
     *
     * @param showErrorWithoutErrorText show the error or not
     */
    @SuppressLint("RestrictedApi")
    public void setShowErrorWithoutErrorText(boolean showErrorWithoutErrorText) {
        this.showErrorWithoutErrorText = showErrorWithoutErrorText;
        if (showErrorWithoutErrorText) {
            mCollapsingTextHelper.setCollapsedTextColor(mErrorTextColor);
        } else {
            boolean isFocused = arrayContains(getDrawableState(), android.R.attr.state_focused);
            if (isFocused) {
                mCollapsingTextHelper.setCollapsedTextColor(mFocusedTextColor);
            } else {
                mCollapsingTextHelper.setCollapsedTextColor(mDefaultTextColor);
            }

        }

        Drawable editTextBackground = mEditText.getBackground();
        if (editTextBackground == null) {
            return;
        }

        ensureBackgroundDrawableStateWorkaround();
        if (android.support.v7.widget.DrawableUtils.canSafelyMutateDrawable(editTextBackground)) {
            editTextBackground = editTextBackground.mutate();
        }
        // Set a color filter of the error color
        editTextBackground.setColorFilter(
                AppCompatDrawableManager.getPorterDuffColorFilter(
                        mCollapsingTextHelper.getCollapsedTextColor().getDefaultColor(), PorterDuff.Mode.SRC_IN));

        invalidate();
    }

    void updateLabelState(final boolean animate, final boolean force) {
        final boolean isEnabled = isEnabled();
        final boolean hasText = mEditText != null && !TextUtils.isEmpty(mEditText.getText());
        final boolean isFocused = arrayContains(getDrawableState(), android.R.attr.state_focused);
        final boolean isErrorShowing = !TextUtils.isEmpty(getError());

        if (mDefaultTextColor != null) {
            mCollapsingTextHelper.setExpandedTextColor(mDefaultTextColor);
        }

        /**
         * show the error hint
         */
        if (isEnabled && showErrorWithoutErrorText) {
            mCollapsingTextHelper.setCollapsedTextColor(mErrorTextColor);
        } else if (isEnabled && mCounterOverflowed && mCounterView != null) {
            mCollapsingTextHelper.setCollapsedTextColor(mCounterView.getTextColors());
        } else if (isEnabled && isFocused && mFocusedTextColor != null) {
            mCollapsingTextHelper.setCollapsedTextColor(mFocusedTextColor);
        } else if (mDefaultTextColor != null) {
            mCollapsingTextHelper.setCollapsedTextColor(mDefaultTextColor);
        }

        if (hasText || (isEnabled() && (isFocused || isErrorShowing))) {
            // We should be showing the label so do so if it isn't already
            if (force || mHintExpanded) {
                collapseHint(animate);
            }
        } else {
            // We should not be showing the label so hide it
            if (force || !mHintExpanded) {
                expandHint(animate);
            }
        }
    }

    /**
     * Returns the {@link EditText} used for text input.
     */
    @Nullable
    public EditText getEditText() {
        return mEditText;
    }

    /**
     * Set the hint to be displayed in the floating label, if enabled.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_android_hint
     * @see #setHintEnabled(boolean)
     */
    public void setHint(@Nullable CharSequence hint) {
        if (mHintEnabled) {
            setHintInternal(hint);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    private void setHintInternal(CharSequence hint) {
        mHint = hint;
        mCollapsingTextHelper.setText(hint);
    }

    /**
     * Returns the hint which is displayed in the floating label, if enabled.
     *
     * @return the hint, or null if there isn't one set, or the hint is not enabled.
     * @attr ref android.support.design.R.styleable#TextInputLayout_android_hint
     */
    @Nullable
    public CharSequence getHint() {
        return mHintEnabled ? mHint : null;
    }

    /**
     * Sets whether the floating label functionality is enabled or not in this layout.
     * <p>
     * <p>If enabled, any non-empty hint in the child EditText will be moved into the floating
     * hint, and its existing hint will be cleared. If disabled, then any non-empty floating hint
     * in this layout will be moved into the EditText, and this layout's hint will be cleared.</p>
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintEnabled
     * @see #setHint(CharSequence)
     * @see #isHintEnabled()
     */
    public void setHintEnabled(boolean enabled) {
        if (enabled != mHintEnabled) {
            mHintEnabled = enabled;

            final CharSequence editTextHint = mEditText.getHint();
            if (!mHintEnabled) {
                if (!TextUtils.isEmpty(mHint) && TextUtils.isEmpty(editTextHint)) {
                    // If the hint is disabled, but we have a hint set, and the EditText doesn't,
                    // pass it through...
                    mEditText.setHint(mHint);
                }
                // Now clear out any set hint
                setHintInternal(null);
            } else {
                if (!TextUtils.isEmpty(editTextHint)) {
                    // If the hint is now enabled and the EditText has one set, we'll use it if
                    // we don't already have one, and clear the EditText's
                    if (TextUtils.isEmpty(mHint)) {
                        setHint(editTextHint);
                    }
                    mEditText.setHint(null);
                }
            }

            // Now update the EditText top margin
            if (mEditText != null) {
                updateInputLayoutMargins();
            }
        }
    }

    /**
     * Returns whether the floating label functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintEnabled
     * @see #setHintEnabled(boolean)
     */
    public boolean isHintEnabled() {
        return mHintEnabled;
    }

    /**
     * Sets the hint text color, size, style from the specified TextAppearance resource.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintTextAppearance
     */
    public void setHintTextAppearance(@StyleRes int resId) {
        mCollapsingTextHelper.setCollapsedTextAppearance(resId);
        mFocusedTextColor = mCollapsingTextHelper.getCollapsedTextColor();

        if (mEditText != null) {
            updateLabelState(false);
            // Text size might have changed so update the top margin
            updateInputLayoutMargins();
        }
    }

    private void addIndicator(TextView indicator, int index) {
        if (mIndicatorArea == null) {
            mIndicatorArea = new LinearLayout(getContext());
            mIndicatorArea.setOrientation(LinearLayout.HORIZONTAL);
            addView(mIndicatorArea, LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);

            // Add a flexible spacer in the middle so that the left/right views stay pinned
            final Space spacer = new Space(getContext());
            final LayoutParams spacerLp = new LayoutParams(0, 0, 1f);
            mIndicatorArea.addView(spacer, spacerLp);

            if (mEditText != null) {
                adjustIndicatorPadding();
            }
        }
        mIndicatorArea.setVisibility(View.VISIBLE);
        mIndicatorArea.addView(indicator, index);
        mIndicatorsAdded++;
    }

    private void adjustIndicatorPadding() {
        // Add padding to the error and character counter so that they match the EditText
        ViewCompat.setPaddingRelative(mIndicatorArea, ViewCompat.getPaddingStart(mEditText),
                0, ViewCompat.getPaddingEnd(mEditText), mEditText.getPaddingBottom());
    }

    private void removeIndicator(TextView indicator) {
        if (mIndicatorArea != null) {
            mIndicatorArea.removeView(indicator);
            if (--mIndicatorsAdded == 0) {
                mIndicatorArea.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Whether the error functionality is enabled or not in this layout. Enabling this
     * functionality before setting an error message via {@link #setError(CharSequence)}, will mean
     * that this layout will not change size when an error is displayed.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_errorEnabled
     */
    public void setErrorEnabled(boolean enabled) {
        if (mErrorEnabled != enabled) {
            if (mErrorView != null) {
                ViewCompat.animate(mErrorView).cancel();
            }

            if (enabled) {
                mErrorView = new AppCompatTextView(getContext());
                mErrorView.setId(R.id.textinput_error);
                if (mTypeface != null) {
                    mErrorView.setTypeface(mTypeface);
                }
                boolean useDefaultColor = false;
                try {
                    TextViewCompat.setTextAppearance(mErrorView, mErrorTextAppearance);

                    if (Build.VERSION.SDK_INT >= 23
                            && mErrorView.getTextColors().getDefaultColor() == Color.MAGENTA) {
                        // Caused by our theme not extending from Theme.Design*. On API 23 and
                        // above, unresolved theme attrs result in MAGENTA rather than an exception.
                        // Flag so that we use a decent default
                        useDefaultColor = true;
                    }
                } catch (Exception e) {
                    // Caused by our theme not extending from Theme.Design*. Flag so that we use
                    // a decent default
                    useDefaultColor = true;
                }
                if (useDefaultColor) {
                    // Probably caused by our theme not extending from Theme.Design*. Instead
                    // we manually set something appropriate
                    TextViewCompat.setTextAppearance(mErrorView,
                            R.style.MultiOperationInputLayout_Caption);
                    mErrorView.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.text_input_error_color_light));
                }
                mErrorView.setVisibility(INVISIBLE);
                ViewCompat.setAccessibilityLiveRegion(mErrorView,
                        ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
                addIndicator(mErrorView, 0);
            } else {
                mErrorShown = false;
                updateEditTextBackground();
                removeIndicator(mErrorView);
                mErrorView = null;
            }
            mErrorEnabled = enabled;
        }
    }

    /**
     * Sets the text color and size for the error message from the specified
     * TextAppearance resource.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_errorTextAppearance
     */
    public void setErrorTextAppearance(@StyleRes int resId) {
        mErrorTextAppearance = resId;
        if (mErrorView != null) {
            TextViewCompat.setTextAppearance(mErrorView, resId);
        }
    }

    /**
     * Returns whether the error functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_errorEnabled
     * @see #setErrorEnabled(boolean)
     */
    public boolean isErrorEnabled() {
        return mErrorEnabled;
    }

    /**
     * Sets an error message that will be displayed below our {@link EditText}. If the
     * {@code error} is {@code null}, the error message will be cleared.
     * <p>
     * If the error functionality has not been enabled via {@link #setErrorEnabled(boolean)}, then
     * it will be automatically enabled if {@code error} is not empty.
     *
     * @param error Error message to display, or null to clear
     * @see #getError()
     */
    public void setError(@Nullable final CharSequence error) {
        // Only animate if we're enabled, laid out, and we have a different error message
        setError(error, ViewCompat.isLaidOut(this) && isEnabled()
                && (mErrorView == null || !TextUtils.equals(mErrorView.getText(), error)));
    }

    private void setError(@Nullable final CharSequence error, final boolean animate) {
        mError = error;

        if (!mErrorEnabled) {
            if (TextUtils.isEmpty(error)) {
                // If error isn't enabled, and the error is empty, just return
                return;
            }
            // Else, we'll assume that they want to enable the error functionality
            setErrorEnabled(true);
        }

        mErrorShown = !TextUtils.isEmpty(error);

        // Cancel any on-going animation
        ViewCompat.animate(mErrorView).cancel();

        if (mErrorShown) {
            mErrorView.setText(error);
            mErrorView.setVisibility(VISIBLE);

            if (animate) {
                if (ViewCompat.getAlpha(mErrorView) == 1f) {
                    // If it's currently 100% show, we'll animate it from 0
                    ViewCompat.setAlpha(mErrorView, 0f);
                }
                ViewCompat.animate(mErrorView)
                        .alpha(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setInterpolator(AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR)
                        .setListener(new ViewPropertyAnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(View view) {
                                view.setVisibility(VISIBLE);
                            }
                        }).start();
            } else {
                // Set alpha to 1f, just in case
                ViewCompat.setAlpha(mErrorView, 1f);
            }
        } else {
            if (mErrorView.getVisibility() == VISIBLE) {
                if (animate) {
                    ViewCompat.animate(mErrorView)
                            .alpha(0f)
                            .setDuration(ANIMATION_DURATION)
                            .setInterpolator(AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR)
                            .setListener(new ViewPropertyAnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(View view) {
                                    mErrorView.setText(error);
                                    view.setVisibility(INVISIBLE);
                                }
                            }).start();
                } else {
                    mErrorView.setText(error);
                    mErrorView.setVisibility(INVISIBLE);
                }
            }
        }

        updateEditTextBackground();
        updateLabelState(animate);
    }

    /**
     * Whether the character counter functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterEnabled
     */
    public void setCounterEnabled(boolean enabled) {
        if (mCounterEnabled != enabled) {
            if (enabled) {
                mCounterView = new AppCompatTextView(getContext());
                mCounterView.setId(R.id.textinput_counter);
                if (mTypeface != null) {
                    mCounterView.setTypeface(mTypeface);
                }
                mCounterView.setMaxLines(1);
                try {
                    TextViewCompat.setTextAppearance(mCounterView, mCounterTextAppearance);
                } catch (Exception e) {
                    // Probably caused by our theme not extending from Theme.Design*. Instead
                    // we manually set something appropriate
                    TextViewCompat.setTextAppearance(mCounterView, R.style.MultiOperationInputLayout_Caption);
                    mCounterView.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.text_input_error_color_light));
                }
                addIndicator(mCounterView, -1);
                if (mEditText == null) {
                    updateCounter(0);
                } else {
                    updateCounter(mEditText.getText().length());
                }
            } else {
                removeIndicator(mCounterView);
                mCounterView = null;
            }
            mCounterEnabled = enabled;
        }
    }

    /**
     * Returns whether the character counter functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterEnabled
     * @see #setCounterEnabled(boolean)
     */
    public boolean isCounterEnabled() {
        return mCounterEnabled;
    }

    /**
     * Sets the max length to display at the character counter.
     *
     * @param maxLength maxLength to display. Any value less than or equal to 0 will not be shown.
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterMaxLength
     */
    public void setCounterMaxLength(int maxLength) {
        if (mCounterMaxLength != maxLength) {
            if (maxLength > 0) {
                mCounterMaxLength = maxLength;
            } else {
                mCounterMaxLength = INVALID_MAX_LENGTH;
            }
            if (mCounterEnabled) {
                updateCounter(mEditText == null ? 0 : mEditText.getText().length());
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Since we're set to addStatesFromChildren, we need to make sure that we set all
        // children to enabled/disabled otherwise any enabled children will wipe out our disabled
        // drawable state
        recursiveSetEnabled(this, enabled);
        super.setEnabled(enabled);
    }

    private static void recursiveSetEnabled(final ViewGroup vg, final boolean enabled) {
        for (int i = 0, count = vg.getChildCount(); i < count; i++) {
            final View child = vg.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                recursiveSetEnabled((ViewGroup) child, enabled);
            }
        }
    }

    /**
     * Returns the max length shown at the character counter.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterMaxLength
     */
    public int getCounterMaxLength() {
        return mCounterMaxLength;
    }

    void updateCounter(int length) {
        boolean wasCounterOverflowed = mCounterOverflowed;
        if (mCounterMaxLength == INVALID_MAX_LENGTH) {
            mCounterView.setText(String.valueOf(length));
            mCounterOverflowed = false;
        } else {
            mCounterOverflowed = length > mCounterMaxLength;
            if (wasCounterOverflowed != mCounterOverflowed) {
                TextViewCompat.setTextAppearance(mCounterView, mCounterOverflowed
                        ? mCounterOverflowTextAppearance : mCounterTextAppearance);
            }
            mCounterView.setText(getContext().getString(R.string.character_counter_pattern,
                    length, mCounterMaxLength));
        }
        if (mEditText != null && wasCounterOverflowed != mCounterOverflowed) {
            updateLabelState(false);
            updateEditTextBackground();
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateEditTextBackground() {
        if (mEditText == null) {
            return;
        }

        Drawable editTextBackground = mEditText.getBackground();
        if (editTextBackground == null) {
            return;
        }

        ensureBackgroundDrawableStateWorkaround();

        if (android.support.v7.widget.DrawableUtils.canSafelyMutateDrawable(editTextBackground)) {
            editTextBackground = editTextBackground.mutate();
        }

        /**
         * show the error hint
         */
        if (showErrorWithoutErrorText) {
            // Set a color filter of the error color
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            mCollapsingTextHelper.getCollapsedTextColor().getDefaultColor(), PorterDuff.Mode.SRC_IN));
        } else if (mErrorShown && mErrorView != null) {
            // Set a color filter of the error color
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            mErrorView.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
        } else if (mCounterOverflowed && mCounterView != null) {
            // Set a color filter of the counter color
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            mCounterView.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
        } else if (stickyEditTextBackgroundColor) {
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            mCollapsingTextHelper.getCollapsedTextColor().getDefaultColor(), PorterDuff.Mode.SRC_IN));
        } else {
            // Else reset the color filter and refresh the drawable state so that the
            // normal tint is used
            DrawableCompat.clearColorFilter(editTextBackground);
            mEditText.refreshDrawableState();
        }
    }

    private void ensureBackgroundDrawableStateWorkaround() {
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk != 21 && sdk != 22) {
            // The workaround is only required on API 21-22
            return;
        }
        final Drawable bg = mEditText.getBackground();
        if (bg == null) {
            return;
        }

        if (!mHasReconstructedEditTextBackground) {
            // This is gross. There is an issue in the platform which affects container Drawables
            // where the first drawable retrieved from resources will propagate any changes
            // (like color filter) to all instances from the cache. We'll try to workaround it...

            final Drawable newBg = bg.getConstantState().newDrawable();

            if (bg instanceof DrawableContainer) {
                // If we have a Drawable container, we can try and set it's constant state via
                // reflection from the new Drawable
                mHasReconstructedEditTextBackground =
                        DrawableUtils.setContainerConstantState(
                                (DrawableContainer) bg, newBg.getConstantState());
            }

            if (!mHasReconstructedEditTextBackground) {
                // If we reach here then we just need to set a brand new instance of the Drawable
                // as the background. This has the unfortunate side-effect of wiping out any
                // user set padding, but I'd hope that use of custom padding on an EditText
                // is limited.
                ViewCompat.setBackground(mEditText, newBg);
                mHasReconstructedEditTextBackground = true;
            }
        }
    }

    static class SavedState extends AbsSavedState {
        CharSequence error;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            error = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);

        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            TextUtils.writeToParcel(error, dest, flags);
        }

        @Override
        public String toString() {
            return "TextInputLayout.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " error=" + error + "}";
        }

        public static final Creator<SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                });
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        if (mErrorShown) {
            ss.error = getError();
        }
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setError(ss.error);
        requestLayout();
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        mRestoringSavedState = true;
        super.dispatchRestoreInstanceState(container);
        mRestoringSavedState = false;
    }

    /**
     * Returns the error message that was set to be displayed with
     * {@link #setError(CharSequence)}, or <code>null</code> if no error was set
     * or if error displaying is not enabled.
     *
     * @see #setError(CharSequence)
     */
    @Nullable
    public CharSequence getError() {
        return mErrorEnabled ? mError : null;
    }

    /**
     * Returns whether any hint state changes, due to being focused or non-empty text, are
     * animated.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintAnimationEnabled
     * @see #setHintAnimationEnabled(boolean)
     */
    public boolean isHintAnimationEnabled() {
        return mHintAnimationEnabled;
    }

    /**
     * Set whether any hint state changes, due to being focused or non-empty text, are
     * animated.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintAnimationEnabled
     * @see #isHintAnimationEnabled()
     */
    public void setHintAnimationEnabled(boolean enabled) {
        mHintAnimationEnabled = enabled;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mHintEnabled) {
            mCollapsingTextHelper.draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateOperationToggleView();
        updateOperationTextView();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setOperationTextString(String str){
        mOperationTextView.setText(str);
    }

    private void updateOperationTextView() {
        if (mEditText == null) {
            // If there is no EditText, there is nothing to update
            return;
        }

        if (shouldShowMultiOperationTextView()) {
            if (mOperationTextView == null) {
                mOperationTextView = (TextView) LayoutInflater.from(getContext())
                        .inflate(R.layout.design_text_input_other_operation_text, mInputFrame, false);
                mOperationTextView.setTextColor(mOperationTextViewColor);
                mOperationTextView.setText(mOperationText);
                mOperationTextView.setTextSize(mOperationTextViewSize);
                mOperationTextView.setOnClickListener(mOperationTextViewOnclickListener);
                mInputFrame.addView(mOperationTextView);
            }


            if (mEditText != null && ViewCompat.getMinimumHeight(mEditText) <= 0) {
                mEditText.setMinimumHeight(ViewCompat.getMinimumHeight(mOperationTextView));
            }

            // We need to add a dummy drawable as the end compound drawable so that the text is
            // indented and doesn't display below the toggle view
            if (mOperationToggleDummyDrawable == null) {
                mOperationToggleDummyDrawable = new ColorDrawable();
            }
            mOperationToggleDummyDrawable.setBounds(0, 0, mOperationTextView.getMeasuredWidth(), 1);


            final Drawable[] compounds = TextViewCompat.getCompoundDrawablesRelative(mEditText);
            // Store the user defined end compound drawable so that we can restore it later
            if (compounds[2] != mOperationToggleDummyDrawable) {
                mOriginalEditTextEndDrawable = compounds[2];
            }
            TextViewCompat.setCompoundDrawablesRelative(mEditText, compounds[0], compounds[1],
                    mOperationToggleDummyDrawable, compounds[3]);

            // Copy over the EditText's padding so that we match
            mOperationTextView.setPadding(mEditText.getPaddingLeft(),
                    mEditText.getPaddingTop(), mEditText.getPaddingRight(),
                    mEditText.getPaddingBottom());
            mOperationTextView.setVisibility(View.VISIBLE);
        } else if (null != mOperationTextView) {
            mOperationTextView.setVisibility(View.GONE);
        }
    }

    private void updateOperationToggleView() {
        if (mEditText == null) {
            // If there is no EditText, there is nothing to update
            return;
        }

        if (shouldShowOperationIcon()) {
            if (mOperationToggleView == null) {
                mOperationToggleView = (CheckableImageButton) LayoutInflater.from(getContext())
                        .inflate(R.layout.design_text_input_password_btn, mInputFrame, false);
                mOperationToggleView.setImageDrawable(mOperationToggleDrawable);
                mOperationToggleView.setContentDescription(mOperationToggleContentDesc);
                mInputFrame.addView(mOperationToggleView);

                if (passwordToggleEnable()) {
                    mOperationToggleView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            passwordVisibilityToggleRequested();
                        }
                    });
                } else {
                    mOperationToggleView.setOnClickListener(mMultiOperationToggleOnclickListener);
                }
                setOperationToggleChecked(mOperationToggleChecked);
            }

            if (mEditText != null && ViewCompat.getMinimumHeight(mEditText) <= 0) {
                mEditText.setMinimumHeight(ViewCompat.getMinimumHeight(mOperationToggleView));
            }

            mOperationToggleView.setVisibility(VISIBLE);

            // We need to add a dummy drawable as the end compound drawable so that the text is
            // indented and doesn't display below the toggle view
            if (mOperationToggleDummyDrawable == null) {
                mOperationToggleDummyDrawable = new ColorDrawable();
            }
            mOperationToggleDummyDrawable.setBounds(0, 0, mOperationToggleView.getMeasuredWidth(), 1);

            final Drawable[] compounds = TextViewCompat.getCompoundDrawablesRelative(mEditText);
            // Store the user defined end compound drawable so that we can restore it later
            if (compounds[2] != mOperationToggleDummyDrawable) {
                mOriginalEditTextEndDrawable = compounds[2];
            }
            TextViewCompat.setCompoundDrawablesRelative(mEditText, compounds[0], compounds[1],
                    mOperationToggleDummyDrawable, compounds[3]);

            // Copy over the EditText's padding so that we match
            mOperationToggleView.setPadding(mEditText.getPaddingLeft(),
                    mEditText.getPaddingTop(), mEditText.getPaddingRight(),
                    mEditText.getPaddingBottom());
        } else {
            if (mOperationToggleView != null && mOperationToggleView.getVisibility() == VISIBLE) {
                mOperationToggleView.setVisibility(View.GONE);
            }

            if (mOperationToggleDummyDrawable != null) {
                // Make sure that we remove the dummy end compound drawable if it exists, and then
                // clear it
                final Drawable[] compounds = TextViewCompat.getCompoundDrawablesRelative(mEditText);
                if (compounds[2] == mOperationToggleDummyDrawable) {
                    TextViewCompat.setCompoundDrawablesRelative(mEditText, compounds[0],
                            compounds[1], mOriginalEditTextEndDrawable, compounds[3]);
                    mOperationToggleDummyDrawable = null;
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    void passwordVisibilityToggleRequested() {
        // Store the current cursor position
        final int selection = mEditText.getSelectionEnd();

        if (hasPasswordTransformation()) {
            mEditText.setTransformationMethod(null);
            mOperationToggleView.setChecked(true);
        } else {
            mEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            mOperationToggleView.setChecked(false);
        }

        // And restore the cursor position
        mEditText.setSelection(selection);
    }

    @SuppressLint("RestrictedApi")
    public void setOperationToggleChecked(boolean checked) {
        if (mOperationToggleView != null) {
            mOperationToggleView.setChecked(checked);

            if (passwordToggleEnable() && checked && hasPasswordTransformation()) {
                mEditText.setTransformationMethod(null);
            } else {
                mEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
        }
    }

    @SuppressLint("RestrictedApi")
    public boolean isOperationToggleChecked() {
        return (null != mOperationToggleView && mOperationToggleView.isChecked());
    }

    private boolean hasPasswordTransformation() {
        return mEditText != null
                && mEditText.getTransformationMethod() instanceof PasswordTransformationMethod;
    }

    private boolean shouldShowOperationIcon() {
        return isOperationToggleVisible();
    }

    private boolean passwordToggleEnable() {
        return mOperationToggleType == OPERATION_TOGGLE_TYPE_PASSWORD;
    }

    private boolean isOperationToggleVisible() {
        return mOperationType == OPERATION_TYPE_EDIT_TEXT_WITH_TOGGLE;
    }

    private boolean shouldShowMultiOperationTextView() {
        return mOperationType == OPERATION_TYPE_EDIT_TEXT_WITH_TEXT;
    }

    private void applyOperationToggleTint() {
        if (mOperationToggleApplyTint && mOperationToggleDrawable != null
                && (mHasOperationToggleTintList || mHasOperationToggleTintMode)) {
            mOperationToggleDrawable = DrawableCompat.wrap(mOperationToggleDrawable).mutate();

            if (mHasOperationToggleTintList) {
                DrawableCompat.setTintList(mOperationToggleDrawable, mOperationToggleTintList);
            }
            if (mHasOperationToggleTintMode) {
                DrawableCompat.setTintMode(mOperationToggleDrawable, mOperationToggleTintMode);
            }

            if (mOperationToggleView != null
                    && mOperationToggleView.getDrawable() != mOperationToggleDrawable) {
                mOperationToggleView.setImageDrawable(mOperationToggleDrawable);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mHintEnabled && mEditText != null) {
            final Rect rect = mTmpRect;
            ViewGroupUtils.getDescendantRect(this, mEditText, rect);

            final int l = rect.left + mEditText.getCompoundPaddingLeft();
            final int r = rect.right - mEditText.getCompoundPaddingRight();

            mCollapsingTextHelper.setExpandedBounds(
                    l, rect.top + mEditText.getCompoundPaddingTop(),
                    r, rect.bottom - mEditText.getCompoundPaddingBottom());

            // Set the collapsed bounds to be the the full height (minus padding) to match the
            // EditText's editable area
            mCollapsingTextHelper.setCollapsedBounds(l, getPaddingTop(),
                    r, bottom - top - getPaddingBottom());

            mCollapsingTextHelper.recalculate();
        }
    }

    private void collapseHint(boolean animate) {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        if (animate && mHintAnimationEnabled) {
            animateToExpansionFraction(1f);
        } else {
            mCollapsingTextHelper.setExpansionFraction(1f);
        }
        mHintExpanded = false;
    }

    @Override
    protected void drawableStateChanged() {
        if (mInDrawableStateChanged) {
            // Some of the calls below will update the drawable state of child views. Since we're
            // using addStatesFromChildren we can get into infinite recursion, hence we'll just
            // exit in this instance
            return;
        }

        mInDrawableStateChanged = true;

        super.drawableStateChanged();

        final int[] state = getDrawableState();
        boolean changed = false;

        // Drawable state has changed so see if we need to update the label
        updateLabelState(ViewCompat.isLaidOut(this) && isEnabled());

        updateEditTextBackground();

        if (mCollapsingTextHelper != null) {
            changed |= mCollapsingTextHelper.setState(state);
        }

        if (changed) {
            invalidate();
        }

        mInDrawableStateChanged = false;
    }

    private void expandHint(boolean animate) {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
        if (animate && mHintAnimationEnabled) {
            animateToExpansionFraction(0f);
        } else {
            mCollapsingTextHelper.setExpansionFraction(0f);
        }
        mHintExpanded = true;
    }

    @VisibleForTesting
    void animateToExpansionFraction(final float target) {
        if (mCollapsingTextHelper.getExpansionFraction() == target) {
            return;
        }
        if (mAnimator == null) {
            mAnimator = new ValueAnimator();
            mAnimator.setInterpolator(AnimationUtils.LINEAR_INTERPOLATOR);
            mAnimator.setDuration(ANIMATION_DURATION);
            mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    mCollapsingTextHelper.setExpansionFraction((float) animator.getAnimatedValue());
                }
            });
        }
        mAnimator.setFloatValues(mCollapsingTextHelper.getExpansionFraction(), target);
        mAnimator.start();
    }

    @VisibleForTesting
    final boolean isHintExpanded() {
        return mHintExpanded;
    }

    private class TextInputAccessibilityDelegate extends AccessibilityDelegateCompat {
        TextInputAccessibilityDelegate() {
        }

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setClassName(MultiOperationInputLayout.class.getSimpleName());
        }

        @Override
        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(host, event);

            final CharSequence text = mCollapsingTextHelper.getText();
            if (!TextUtils.isEmpty(text)) {
                event.getText().add(text);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(MultiOperationInputLayout.class.getSimpleName());

            final CharSequence text = mCollapsingTextHelper.getText();
            if (!TextUtils.isEmpty(text)) {
                info.setText(text);
            }
            if (mEditText != null) {
                info.setLabelFor(mEditText);
            }
            final CharSequence error = mErrorView != null ? mErrorView.getText() : null;
            if (!TextUtils.isEmpty(error)) {
                info.setContentInvalid(true);
                info.setError(error);
            }
        }
    }

    private static boolean arrayContains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * add the OnclickListener to the right toggle
     *
     * @param onclickListener the listener
     */
    public void setOperationTextViewOnclickListener(OnClickListener onclickListener) {
        this.mOperationTextViewOnclickListener = onclickListener;
        if (mOperationTextView != null) {
            mOperationTextView.setOnClickListener(mOperationTextViewOnclickListener);
        }
    }

    /**
     * add the OnclickListener to the right textView
     *
     * @param onclickListener the listener
     */
    public void setOperationToggleOnclickListener(OnClickListener onclickListener) {
        if (!passwordToggleEnable()) {
            this.mMultiOperationToggleOnclickListener = onclickListener;
            if (mOperationToggleView != null) {
                mOperationToggleView.setOnClickListener(mMultiOperationToggleOnclickListener);
            }
        }
    }

    public boolean isShowErrorWithoutErrorText() {
        return showErrorWithoutErrorText;
    }

    public void useStikyEditTextBackground(boolean use) {
        this.stickyEditTextBackgroundColor = use;
    }
}