package ch.deletescape.lawnchair.views

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.view.animation.AccelerateInterpolator
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.blur.BlurDrawable
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import ch.deletescape.lawnchair.blurWallpaperProvider
import ch.deletescape.lawnchair.graphics.NinePatchDrawHelper
import ch.deletescape.lawnchair.round
import com.android.launcher3.*
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.dynamicui.WallpaperColorInfo
import com.android.launcher3.graphics.GradientView
import com.android.launcher3.graphics.ShadowGenerator

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class AllAppsScrim(context: Context, attrs: AttributeSet?)
    : GradientView(context, attrs), Insettable {
    private val pStyle = FeatureFlags.LAUNCHER3_P_ALL_APPS
    private val prefs by lazy { LawnchairPreferences.getInstance(context) }

    private val mFillPaint = Paint(1)
    private val mDrawRect = Rect()
    private val mPadding = Rect()
    private val mInsets = Rect()
    private val mShadowHelper by lazy { NinePatchDrawHelper() }
    private val mRadius get() = dpToPx(prefs.dockRadius).round()
    private val mRounded get() = mRadius.compareTo(0f) != 0
    private val mShadowBlur by lazy { resources.getDimension(R.dimen.all_apps_scrim_blur) }
    private val mDeviceProfile by lazy { Launcher.getLauncher(context).deviceProfile }
    private var mShadowBitmap = generateShadowBitmap()

    private var mDrawOffsetY = 0f
    private var mDrawHeight = 0f
    private val mAccelerator by lazy { AccelerateInterpolator() }

    private val drawingFlatColor = pStyle && mDeviceProfile.isVerticalBarLayout
    private val mMinAlpha get() = if (drawingFlatColor) 0 else prefs.allAppsStartAlpha
    private val mAlphaRange get() = prefs.allAppsEndAlpha - mMinAlpha
    private val maxScrimAlpha = 0.5f
    private var scrimColor = 0
    private var remainingScreenColor = 0
    private var remainingScreenPathValid = false
    private val tempPath = Path()
    private val remainingScreenPath = Path()
    private val remainingScreenPaint = Paint()
    private val wallpaperColorInfo = WallpaperColorInfo.getInstance(context)

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable?, what: Runnable?) {

            }

            override fun invalidateDrawable(who: Drawable?) {
                invalidateDrawRect()
            }

            override fun scheduleDrawable(who: Drawable?, what: Runnable?, `when`: Long) {

            }
        }
    }

    private val blurRadius get() = if (pStyle && !drawingFlatColor) mRadius else 0f
    private var blurDrawable = createBlurDrawable()

    private val enableShadow get() = prefs.dockShadow
    private val maxRadiusSpace = dpToPx(8f)

    init {
        updateColors()
    }

    override fun updateColors() {
        super.updateColors()

        if (pStyle) {
            mFillPaint.color = mScrimColor
        }
    }

    override fun createRadialShader() {
        if (!pStyle) super.createRadialShader()
    }

    fun getTop(progress: Float, shiftRange: Float): Float {
        if (interpolateAlpha(1 - progress) > 176) {
            if (drawingFlatColor) return 0f
            if (height != 0) {
                val offsetY = -shiftRange * (1 - progress)
                return height.toFloat() + offsetY - mDrawHeight + mPadding.top.toFloat() - Math.min(maxRadiusSpace, mRadius)
            }
        }
        return shiftRange
    }

    override fun onDraw(canvas: Canvas) {
        if (drawingFlatColor) {
            blurDrawable?.draw(canvas)
            canvas.drawPaint(mFillPaint)
        } else if (pStyle) {
            val radius = mRadius
            val height = height.toFloat() + mDrawOffsetY - mDrawHeight + mPadding.top.toFloat() - Math.min(maxRadiusSpace, radius)
            val width = (width - mPadding.right).toFloat()
            if (remainingScreenColor != 0) {
                if (!remainingScreenPathValid) {
                    tempPath.reset()
                    tempPath.addRoundRect(0f, getHeight().toFloat() - radius, getWidth().toFloat(), 10f + (getHeight().toFloat() + radius), radius, radius, Path.Direction.CW)
                    remainingScreenPath.reset()
                    remainingScreenPath.addRect(0f, 0f, getWidth().toFloat(), getHeight().toFloat(), Path.Direction.CW)
                    remainingScreenPath.op(tempPath, Path.Op.DIFFERENCE)
                    remainingScreenPathValid = true
                }
                remainingScreenPaint.color = remainingScreenColor
                val scrimTranslation = getHeight() - height - radius
                canvas.translate(0f, -scrimTranslation)
                canvas.drawPath(remainingScreenPath, remainingScreenPaint)
                canvas.translate(0f, scrimTranslation)
            }
            blurDrawable?.run {
                setBounds(mPadding.left, height.toInt(), width.toInt(), (getHeight().toFloat() + radius).toInt())
                draw(canvas)
            }
            if (mRounded && enableShadow) {
                val f = mPadding.left.toFloat() - mShadowBlur
                val f2 = height - mShadowBlur
                val f3 = mShadowBlur + width
                if (mPadding.left <= 0 && mPadding.right <= 0) {
                    mShadowHelper.draw(mShadowBitmap, canvas, f, f2, f3)
                } else {
                    val height2 = getHeight().toFloat()
                    val height3 = mShadowBitmap.height
                    mShadowHelper.mSrc.top = height3 - 5
                    mShadowHelper.mSrc.bottom = height3
                    mShadowHelper.mDst.top = f2 + height3.toFloat()
                    mShadowHelper.mDst.bottom = height2
                    mShadowHelper.draw3Patch(mShadowBitmap, canvas, f, f3)
                }
            }
            canvas.drawRoundRect(mPadding.left.toFloat(), height, width, getHeight().toFloat() + radius, radius, radius, mFillPaint)
        } else {
            blurDrawable?.draw(canvas)
            super.onDraw(canvas)
        }
    }

    fun invalidateDrawRect() {
        mDrawRect.top = (height.toFloat() + mDrawOffsetY - mDrawHeight + mPadding.top.toFloat() - mShadowBlur - 0.5f).toInt()
        invalidate(mDrawRect)
    }

    override fun setInsets(insets: Rect) {
        mInsets.set(insets)
        if (mDeviceProfile.isVerticalBarLayout) {
            mPadding.set(mDeviceProfile.getWorkspacePadding(null, insets))
            mPadding.bottom = 0
            mPadding.left += mInsets.left
            mPadding.top = mInsets.top
            mPadding.right += mInsets.right
            mDrawHeight = 0f
        } else {
            mPadding.setEmpty()
            mDrawHeight = getHotseatHeight(insets).toFloat()
        }
        updateDrawRect(mDeviceProfile)
        invalidate()
    }

    private fun getHotseatHeight(insets: Rect): Int {
        return mDeviceProfile.originalHotseatBarSizePx + if (insets.bottom != 0)
            insets.bottom else mDeviceProfile.mBottomMarginHw
    }

    private fun updateDrawRect(deviceProfile: DeviceProfile) {
        mDrawRect.bottom = height
        if (deviceProfile.isVerticalBarLayout) {
            mDrawRect.left = (mPadding.left.toFloat() - mShadowBlur - 0.5f).toInt()
            mDrawRect.right = ((width - mPadding.right).toFloat() + 0.5f).toInt()
            return
        }
        mDrawRect.left = 0
        mDrawRect.right = width
    }

    override fun setProgress(progress: Float, shiftRange: Float) {
        if (pStyle) {
            mFillPaint.alpha = interpolateAlpha(progress).toInt()
            if (drawingFlatColor) {
                blurDrawable?.alpha = (progress * 255).toInt()
                invalidate()
            } else {
                remainingScreenColor = ColorUtils.setAlphaComponent(scrimColor, (progress * maxScrimAlpha * 255).toInt())
                mDrawOffsetY = -shiftRange * progress
                invalidateDrawRect()
            }
        } else {
            super.setProgress(progress, shiftRange)
            blurDrawable?.alpha = (progress * 255).toInt()
        }
    }

    private fun interpolateAlpha(progress: Float) = mMinAlpha + mAlphaRange * mAccelerator.getInterpolation(progress)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (pStyle) {
            blurDrawable?.setBounds(left, top, right, bottom)
        } else {
            val screenSize = Utilities.getScreenSize(context)
            blurDrawable?.setBounds(0, 0, screenSize.first, screenSize.second)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!pStyle) {
            prefs.addOnPreferenceChangeListener(this,
                    "pref_hotseatShouldUseCustomOpacity",
                    "pref_hotseatCustomOpacity",
                    "pref_allAppsShouldUseCustomOpacity",
                    "pref_allAppsOpacitySB")
        }
        blurDrawable?.startListening()
        wallpaperColorInfo.addOnChangeListener(this)
        onExtractedColorsChanged(wallpaperColorInfo)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (!pStyle) {
            prefs.removeOnPreferenceChangeListener(this,
                    "pref_hotseatShouldUseCustomOpacity",
                    "pref_hotseatCustomOpacity",
                    "pref_allAppsShouldUseCustomOpacity",
                    "pref_allAppsOpacitySB")
        }
        blurDrawable?.stopListening()
        wallpaperColorInfo.removeOnChangeListener(this)
    }

    override fun setTranslationX(translationX: Float) {
        super.setTranslationX(translationX)

        if (pStyle) blurDrawable?.setPotitionX(translationX)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        remainingScreenPathValid = false
    }

    override fun onExtractedColorsChanged(info: WallpaperColorInfo) {
        super.onExtractedColorsChanged(info)
        scrimColor = info.mainColor
    }

    private fun dpToPx(dp: Float): Float {
        return (dp * context.resources.displayMetrics.density + 0.5f)
    }

    fun reset() {
        remainingScreenPathValid = false
        mShadowBitmap = generateShadowBitmap()
        blurDrawable = createBlurDrawable()
    }

    private fun generateShadowBitmap(): Bitmap {
        val tmp = mRadius + mShadowBlur
        val builder = ShadowGenerator.Builder(0)
        builder.radius = mRadius
        builder.shadowBlur = mShadowBlur
        val round = 2 * Math.round(tmp) + 20
        val bitmap = Bitmap.createBitmap(round, round / 2, Bitmap.Config.ARGB_8888)
        val f = 2.0f * tmp + 20.0f - mShadowBlur
        builder.bounds.set(mShadowBlur, mShadowBlur, f, f)
        builder.drawShadow(Canvas(bitmap))
        return bitmap
    }

    private fun createBlurDrawable(): BlurDrawable? {
        blurDrawable?.apply { if (isAttachedToWindow) stopListening() }
        return if (BlurWallpaperProvider.isEnabled) {
            context.blurWallpaperProvider.createDrawable(blurRadius, false).apply { callback = blurDrawableCallback }
        } else {
            null
        }?.apply {
            setBounds(left, top, right, bottom)
            if (isAttachedToWindow) startListening()
        }
    }
}
