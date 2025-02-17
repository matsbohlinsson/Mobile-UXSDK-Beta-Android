/*
 * Copyright (c) 2018-2019 DJI
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dji.ux.beta.widget.vps;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.Dimension;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.ImageView;
import android.widget.TextView;
import dji.thirdparty.io.reactivex.Flowable;
import dji.thirdparty.io.reactivex.android.schedulers.AndroidSchedulers;
import dji.thirdparty.io.reactivex.disposables.Disposable;
import dji.ux.beta.R;
import dji.ux.beta.base.ConstraintLayoutWidget;
import dji.ux.beta.base.DJISDKModel;
import dji.ux.beta.base.GlobalPreferencesManager;
import dji.ux.beta.base.uxsdkkeys.ObservableInMemoryKeyedStore;
import dji.ux.beta.util.DisplayUtil;
import dji.ux.beta.util.UnitConversionUtil;
import java.text.DecimalFormat;

/**
 * Shows the status of the vision positioning system
 * as well as the height of the aircraft as received from the
 * vision positioning system if available.
 *
 * Uses the unit set in the UNIT_TYPE global preferences
 * {@link dji.ux.beta.base.GlobalPreferencesInterface#getUnitType()} and the
 * {@link dji.ux.beta.base.uxsdkkeys.GlobalPreferenceKeys#UNIT_TYPE} UX Key
 * and defaults to meters if nothing is set.
 */
public class VPSWidget extends ConstraintLayoutWidget {
    //region Constants
    private static final int EMS = 2;
    private static final float MIN_VPS_HEIGHT = 1.2f;
    //endregion

    //region Fields
    private static DecimalFormat decimalFormat = new DecimalFormat("##0.0");
    private TextView vpsTitleTextView;
    private ImageView vpsImageView;
    private TextView vpsValueTextView;
    private TextView vpsUnitTextView;
    private Drawable vpsEnabledDrawable;
    private Drawable vpsDisabledDrawable;
    @ColorInt
    private int enabledColor;
    @ColorInt
    private int disabledColor;
    private VPSWidgetModel widgetModel;
    //endregion

    //region Constructors
    public VPSWidget(Context context) {
        super(context);
    }

    public VPSWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VPSWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        inflate(context, R.layout.uxsdk_widget_base_dashboard_image_and_text, this);
        vpsTitleTextView = findViewById(R.id.textview_title);
        vpsImageView = findViewById(R.id.imageview_icon);
        vpsValueTextView = findViewById(R.id.textview_value);
        vpsUnitTextView = findViewById(R.id.textview_unit);

        if (!isInEditMode()) {
            widgetModel = new VPSWidgetModel(DJISDKModel.getInstance(),
                                             ObservableInMemoryKeyedStore.getInstance(),
                                             GlobalPreferencesManager.getInstance());
            vpsTitleTextView.setText(getResources().getString(R.string.uxsdk_vps_title));
            vpsValueTextView.setMinEms(EMS);
        }

        vpsEnabledDrawable = getResources().getDrawable(R.drawable.uxsdk_ic_vps_enabled);
        vpsDisabledDrawable = getResources().getDrawable(R.drawable.uxsdk_ic_vps_disabled);
        enabledColor = getResources().getColor(R.color.uxsdk_white);
        disabledColor = getResources().getColor(R.color.uxsdk_red);

        if (attrs != null) {
            initAttributes(context, attrs);
        }
    }
    //endregion

    //region Lifecycle
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            widgetModel.setup();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            widgetModel.cleanup();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void reactToModelChanges() {
        addReaction(reactToVPSChange());
        addReaction(widgetModel.getUnitType()
                               .observeOn(AndroidSchedulers.mainThread())
                               .subscribe(this::updateUnitText));
    }
    //endregion

    //region Reaction Helpers
    private Disposable reactToVPSChange() {
        return Flowable.combineLatest(widgetModel.getVisionPositioningEnabled(),
                                      widgetModel.getUltrasonicBeingUsed(),
                                      widgetModel.getUltrasonicHeight(),
                                      (isVisionPositioningEnabled, isUltrasonicBeingUsed, ultrasonicHeight) -> Pair.create(
                                          (isVisionPositioningEnabled && isUltrasonicBeingUsed),
                                          ultrasonicHeight))
                       .observeOn(AndroidSchedulers.mainThread())
                       .subscribe(values -> updateUI(values.first, values.second));
    }

    private void updateUI(boolean isVPSEnabledAndUsed, float ultrasonicHeight) {
        if (!isVPSEnabledAndUsed) {
            vpsValueTextView.setText(getResources().getString(R.string.uxsdk_string_default_value));
            vpsValueTextView.setTextColor(disabledColor);
            vpsUnitTextView.setVisibility(GONE);
            vpsImageView.setImageDrawable(vpsDisabledDrawable);
        } else {
            vpsUnitTextView.setVisibility(VISIBLE);
            vpsImageView.setImageDrawable(vpsEnabledDrawable);
            vpsValueTextView.setText(decimalFormat.format(ultrasonicHeight));
            if (ultrasonicHeight <= MIN_VPS_HEIGHT) {
                vpsValueTextView.setTextColor(disabledColor);
            } else {
                vpsValueTextView.setTextColor(enabledColor);
            }
        }
    }
    private void updateUnitText(UnitConversionUtil.UnitType unitType) {
        if (unitType == UnitConversionUtil.UnitType.IMPERIAL) {
            vpsUnitTextView.setText(getResources().getString(R.string.uxsdk_unit_feet));
        } else {
            vpsUnitTextView.setText(getResources().getString(R.string.uxsdk_unit_meters));
        }
    }
    //endregion

    //region Customization
    @NonNull
    @Override
    public String getIdealDimensionRatioString() {
        return getResources().getString(R.string.uxsdk_widget_base_dashboard_distance_ratio);
    }
    //endregion

    //region Customization Helpers

    /**
     * Set text appearance of the vision positioning system status title text view
     *
     * @param textAppearance Style resource for text appearance
     */
    public void setVPSTitleTextAppearance(@StyleRes int textAppearance) {
        vpsTitleTextView.setTextAppearance(getContext(), textAppearance);
    }

    /**
     * Set text color state list for the vision positioning system status title text view
     *
     * @param colorStateList ColorStateList resource
     */
    public void setVPSTitleTextColor(@NonNull ColorStateList colorStateList) {
        vpsTitleTextView.setTextColor(colorStateList);
    }

    /**
     * Set the text color for the vision positioning system status title text view
     *
     * @param color color integer resource
     */
    public void setVPSTitleTextColor(@ColorInt int color) {
        vpsTitleTextView.setTextColor(color);
    }

    /**
     * Get current text color state list of the vision positioning system status title text view
     *
     * @return ColorStateList resource
     */
    @Nullable
    public ColorStateList getVPSTitleTextColors() {
        return vpsTitleTextView.getTextColors();
    }

    /**
     * Get current text color of the vision positioning system status title text view
     *
     * @return color integer resource
     */
    @ColorInt
    public int getVPSTitleTextColor() {
        return vpsTitleTextView.getCurrentTextColor();
    }

    /**
     * Set the text size of the vision positioning system status title text view
     *
     * @param textSize text size float value
     */
    public void setVPSTitleTextSize(@Dimension float textSize) {
        vpsTitleTextView.setTextSize(textSize);
    }

    /**
     * Get current text size of the vision positioning system status title text view
     *
     * @return text size of the text view
     */
    @Dimension
    public float getVPSTitleTextSize() {
        return vpsTitleTextView.getTextSize();
    }

    /**
     * Set the background for the vision positioning system status title text view
     *
     * @param drawable Drawable resource for the background
     */
    public void setVPSTitleTextBackground(@Nullable Drawable drawable) {
        vpsTitleTextView.setBackground(drawable);
    }

    /**
     * Set the resource ID for the background of the vision positioning system status title text
     * view
     *
     * @param resourceId Integer ID of the text view's background resource
     */
    public void setVPSTitleTextBackground(@DrawableRes int resourceId) {
        vpsTitleTextView.setBackgroundResource(resourceId);
    }

    /**
     * Get current background of the vision positioning system status title text view
     *
     * @return Drawable resource of the background
     */
    @Nullable
    public Drawable getVPSTitleTextBackground() {
        return vpsTitleTextView.getBackground();
    }

    /**
     * Set the resource ID for the vision positioning system enabled icon
     *
     * @param resourceId Integer ID of the drawable resource
     */
    public void setVPSEnabledIcon(@DrawableRes int resourceId) {
        vpsEnabledDrawable = getResources().getDrawable(resourceId);
    }

    /**
     * Set the drawable resource for the vision positioning system enabled icon
     *
     * @param icon Drawable resource for the image
     */
    public void setVPSEnabledIcon(@Nullable Drawable icon) {
        vpsEnabledDrawable = icon;
    }

    /**
     * Set the resource ID for the vision positioning system disabled icon
     *
     * @param resourceId Integer ID of the drawable resource
     */
    public void setVPSDisabledIcon(@DrawableRes int resourceId) {
        vpsDisabledDrawable = getResources().getDrawable(resourceId);
    }

    /**
     * Set the drawable resource for the vision positioning system disabled icon
     *
     * @param icon Drawable resource for the image
     */
    public void setVPSDisabledIcon(@Nullable Drawable icon) {
        vpsDisabledDrawable = icon;
    }

    /**
     * Get the drawable resource for the vision positioning system enabled icon
     *
     * @return Drawable resource for the icon
     */
    @Nullable
    public Drawable getVPSEnabledIcon() {
        return vpsEnabledDrawable;
    }

    /**
     * Get the drawable resource for the vision positioning system disabled icon
     *
     * @return Drawable resource for the icon
     */
    @Nullable
    public Drawable getVPSDisabledIcon() {
        return vpsDisabledDrawable;
    }

    /**
     * Set the resource ID for the vision positioning system icon's background
     *
     * @param resourceId Integer ID of the background resource
     */
    public void setVPSIconBackground(@DrawableRes int resourceId) {
        vpsImageView.setBackgroundResource(resourceId);
    }

    /**
     * Set the drawable resource for the vision positioning system icon's background
     *
     * @param background Drawable resource for the background
     */
    public void setVPSIconBackground(@Nullable Drawable background) {
        vpsImageView.setBackground(background);
    }

    /**
     * Get the background drawable resource for the vision positioning system icon
     *
     * @return Drawable for the icon's background
     */
    @Nullable
    public Drawable getVPSIconBackground() {
        return vpsImageView.getBackground();
    }

    /**
     * Set text appearance of the vision positioning system status value text view
     *
     * @param textAppearance Style resource for text appearance
     */
    public void setVPSValueTextAppearance(@StyleRes int textAppearance) {
        vpsValueTextView.setTextAppearance(getContext(), textAppearance);
    }

    /**
     * Set the text color for the vision positioning system status enabled value text view
     *
     * @param color color integer resource
     */
    public void setVPSValueEnabledTextColor(@ColorInt int color) {
        enabledColor = color;
    }

    /**
     * Get current text color of the vision positioning system status enabled value text view
     *
     * @return color integer resource
     */
    @ColorInt
    public int getVPSValueEnabledTextColor() {
        return enabledColor;
    }

    /**
     * Set the text color for the vision positioning system status disabled value text view
     *
     * @param color color integer resource
     */
    public void setVPSValueDisabledTextColor(@ColorInt int color) {
        disabledColor = color;
    }

    /**
     * Get current text color of the vision positioning system status disabled value text view
     *
     * @return color integer resource
     */
    @ColorInt
    public int getVPSValueDisabledTextColor() {
        return disabledColor;
    }

    /**
     * Set the text size of the vision positioning system status value text view
     *
     * @param textSize text size float value
     */
    public void setVPSValueTextSize(@Dimension float textSize) {
        vpsValueTextView.setTextSize(textSize);
    }

    /**
     * Get current text size of the vision positioning system status value text view
     *
     * @return text size of the text view
     */
    @Dimension
    public float getVPSValueTextSize() {
        return vpsValueTextView.getTextSize();
    }

    /**
     * Set the background for the vision positioning system status value text view
     *
     * @param drawable Drawable resource for the background
     */
    public void setVPSValueTextBackground(@Nullable Drawable drawable) {
        vpsValueTextView.setBackground(drawable);
    }

    /**
     * Set the resource ID for the background of the vision positioning system status value text
     * view
     *
     * @param resourceId Integer ID of the text view's background resource
     */
    public void setVPSValueTextBackground(@DrawableRes int resourceId) {
        vpsValueTextView.setBackgroundResource(resourceId);
    }

    /**
     * Get current background of the vision positioning system status value text view
     *
     * @return Drawable resource of the background
     */
    @Nullable
    public Drawable getVPSValueTextBackground() {
        return vpsValueTextView.getBackground();
    }

    /**
     * Set text appearance of the vision positioning system status unit text view
     *
     * @param textAppearance Style resource for text appearance
     */
    public void setVPSUnitTextAppearance(@StyleRes int textAppearance) {
        vpsUnitTextView.setTextAppearance(getContext(), textAppearance);
    }

    /**
     * Set text color state list for the vision positioning system status  unit text view
     *
     * @param colorStateList ColorStateList resource
     */
    public void setVPSUnitTextColor(@NonNull ColorStateList colorStateList) {
        vpsUnitTextView.setTextColor(colorStateList);
    }

    /**
     * Set the text color for the vision positioning system status unit text view
     *
     * @param color color integer resource
     */
    public void setVPSUnitTextColor(@ColorInt int color) {
        vpsUnitTextView.setTextColor(color);
    }

    /**
     * Get current text color state list of the vision positioning system status unit text view
     *
     * @return ColorStateList resource
     */
    @Nullable
    public ColorStateList getVPSUnitTextColors() {
        return vpsUnitTextView.getTextColors();
    }

    /**
     * Get current text color of the vision positioning system status unit text view
     *
     * @return color integer resource
     */
    @ColorInt
    public int getVPSUnitTextColor() {
        return vpsUnitTextView.getCurrentTextColor();
    }

    /**
     * Set the text size of the vision positioning system status unit text view
     *
     * @param textSize text size float value
     */
    public void setVPSUnitTextSize(@Dimension float textSize) {
        vpsUnitTextView.setTextSize(textSize);
    }

    /**
     * Get current text size of the vision positioning system status unit text view
     *
     * @return text size of the text view
     */
    @Dimension
    public float getVPSUnitTextSize() {
        return vpsUnitTextView.getTextSize();
    }

    /**
     * Set the background for the vision positioning system status unit text view
     *
     * @param drawable Drawable resource for the background
     */
    public void setVPSUnitTextBackground(@Nullable Drawable drawable) {
        vpsUnitTextView.setBackground(drawable);
    }

    /**
     * Set the resource ID for the background of the vision positioning system status unit text
     * view
     *
     * @param resourceId Integer ID of the text view's background resource
     */
    public void setVPSUnitTextBackground(@DrawableRes int resourceId) {
        vpsUnitTextView.setBackgroundResource(resourceId);
    }

    /**
     * Get current background of the vision positioning system status unit text view
     *
     * @return Drawable resource of the background
     */
    @Nullable
    public Drawable getVPSUnitTextBackground() {
        return vpsUnitTextView.getBackground();
    }

    //Initialize all customizable attributes
    private void initAttributes(@NonNull Context context, @NonNull AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.VPSWidget);
        int vpsTitleTextAppearanceId =
            typedArray.getResourceId(R.styleable.VPSWidget_uxsdk_vpsTitleTextAppearance, INVALID_RESOURCE);
        if (vpsTitleTextAppearanceId != INVALID_RESOURCE) {
            setVPSTitleTextAppearance(vpsTitleTextAppearanceId);
        }

        float vpsTitleTextSize =
            typedArray.getDimension(R.styleable.VPSWidget_uxsdk_vpsTitleTextSize, INVALID_RESOURCE);
        if (vpsTitleTextSize != INVALID_RESOURCE) {
            setVPSTitleTextSize(DisplayUtil.pxToSp(context, vpsTitleTextSize));
        }

        int vpsTitleTextColor = typedArray.getColor(R.styleable.VPSWidget_uxsdk_vpsTitleTextColor, INVALID_COLOR);
        if (vpsTitleTextColor != INVALID_COLOR) {
            setVPSTitleTextColor(vpsTitleTextColor);
        }

        Drawable vpsTitleTextBackgroundDrawable =
            typedArray.getDrawable(R.styleable.VPSWidget_uxsdk_vpsTitleBackgroundDrawable);
        if (vpsTitleTextBackgroundDrawable != null) {
            setVPSTitleTextBackground(vpsTitleTextBackgroundDrawable);
        }

        Drawable vpsEnabledIcon = typedArray.getDrawable(R.styleable.VPSWidget_uxsdk_vpsEnabledIcon);
        if (vpsEnabledIcon != null) {
            setVPSEnabledIcon(vpsEnabledIcon);
        }

        Drawable vpsDisabledIcon = typedArray.getDrawable(R.styleable.VPSWidget_uxsdk_vpsDisabledIcon);
        if (vpsDisabledIcon != null) {
            setVPSDisabledIcon(vpsDisabledIcon);
        }

        int vpsValueTextAppearanceId =
            typedArray.getResourceId(R.styleable.VPSWidget_uxsdk_vpsValueTextAppearance, INVALID_RESOURCE);
        if (vpsValueTextAppearanceId != INVALID_RESOURCE) {
            setVPSValueTextAppearance(vpsValueTextAppearanceId);
        }

        float vpsValueTextSize =
            typedArray.getDimension(R.styleable.VPSWidget_uxsdk_vpsValueTextSize, INVALID_RESOURCE);
        if (vpsValueTextSize != INVALID_RESOURCE) {
            setVPSValueTextSize(DisplayUtil.pxToSp(context, vpsValueTextSize));
        }

        int vpsValueEnabledTextColor =
            typedArray.getColor(R.styleable.VPSWidget_uxsdk_vpsValueEnabledTextColor, INVALID_COLOR);
        if (vpsValueEnabledTextColor != INVALID_COLOR) {
            setVPSValueEnabledTextColor(vpsValueEnabledTextColor);
        }

        int vpsValueDisabledTextColor =
            typedArray.getColor(R.styleable.VPSWidget_uxsdk_vpsValueDisabledTextColor, INVALID_COLOR);
        if (vpsValueDisabledTextColor != INVALID_COLOR) {
            setVPSValueDisabledTextColor(vpsValueDisabledTextColor);
        }

        Drawable vpsValueTextBackgroundDrawable =
            typedArray.getDrawable(R.styleable.VPSWidget_uxsdk_vpsValueBackgroundDrawable);
        if (vpsValueTextBackgroundDrawable != null) {
            setVPSValueTextBackground(vpsValueTextBackgroundDrawable);
        }

        int vpsUnitTextAppearanceId =
            typedArray.getResourceId(R.styleable.VPSWidget_uxsdk_vpsUnitTextAppearance, INVALID_RESOURCE);
        if (vpsUnitTextAppearanceId != INVALID_RESOURCE) {
            setVPSUnitTextAppearance(vpsUnitTextAppearanceId);
        }

        float vpsUnitTextSize = typedArray.getDimension(R.styleable.VPSWidget_uxsdk_vpsUnitTextSize, INVALID_RESOURCE);
        if (vpsUnitTextSize != INVALID_RESOURCE) {
            setVPSUnitTextSize(DisplayUtil.pxToSp(context, vpsUnitTextSize));
        }

        int vpsUnitTextColor = typedArray.getColor(R.styleable.VPSWidget_uxsdk_vpsUnitTextColor, INVALID_COLOR);
        if (vpsUnitTextColor != INVALID_COLOR) {
            setVPSUnitTextColor(vpsUnitTextColor);
        }

        Drawable vpsUnitTextBackgroundDrawable =
            typedArray.getDrawable(R.styleable.VPSWidget_uxsdk_vpsUnitBackgroundDrawable);
        if (vpsUnitTextBackgroundDrawable != null) {
            setVPSUnitTextBackground(vpsUnitTextBackgroundDrawable);
        }
        typedArray.recycle();
    }
    //endregion
}
