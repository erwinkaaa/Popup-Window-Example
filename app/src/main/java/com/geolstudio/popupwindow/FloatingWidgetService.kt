package com.geolstudio.popupwindow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp


/**
 * Created by GeolseuDei on 09/01/2020.
 */
class FloatingWidgetService : Service(), View.OnClickListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidgetView: View
    private lateinit var collapsedView: View
    private lateinit var expandedView: View
    private lateinit var removeView: ImageView
    private lateinit var removeFloatingWidgetView: View

    private var szWindow = Point()

    private var x_init_cord: Int = 0
    private var y_init_cord: Int = 0
    private var x_init_margin: Int = 0
    private var y_init_margin: Int = 0

    // Variable to check if the Floating widget view is on left side or in right side
    // initially we are displaying Floating widget view to Left side so set it to true
    private var isLeft = true

    private var counter = 0

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        getWindowManagerDefaultDisplay()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        addRemoveView(inflater)
        addFloatingWidgetView(inflater)
        implementClickListeners()
        implementTouchListenerToFloatingWidgetView()
    }

    private fun addRemoveView(inflater: LayoutInflater) {
        removeFloatingWidgetView = inflater.inflate(R.layout.remove_floating_widget_layout, null)

        val LAYOUT_FLAG: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val paramRemove = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            LAYOUT_FLAG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        paramRemove.gravity = Gravity.TOP or Gravity.START

        removeFloatingWidgetView.visibility = View.GONE
        removeView = removeFloatingWidgetView.findViewById(R.id.remove_popup_window)

        windowManager.addView(removeFloatingWidgetView, paramRemove)
    }

    private fun addFloatingWidgetView(inflater: LayoutInflater) {
        floatingWidgetView = inflater.inflate(R.layout.floating_widget_layout, null)

        val LAYOUT_FLAG: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            LAYOUT_FLAG,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        params.x = 0
        params.y = 100

        windowManager.addView(floatingWidgetView, params)

        collapsedView = floatingWidgetView.findViewById(R.id.collapse_view)

        expandedView = floatingWidgetView.findViewById(R.id.expanded_container)
        setExpandedSize()
    }

    private fun getWindowManagerDefaultDisplay() {
        windowManager.defaultDisplay.getSize(szWindow)
    }

    private fun implementTouchListenerToFloatingWidgetView() {
        floatingWidgetView.setOnTouchListener(object : View.OnTouchListener {

            var time_start: Long = 0
            var time_end: Long = 0

            var isLongClick = false
            var inBounded = false

            var remove_img_width = 0
            var remove_img_height: Int = 0

            var handler_longClick: Handler = Handler()
            var runnable_longClick = Runnable {
                isLongClick = true
                removeFloatingWidgetView.visibility = View.VISIBLE
                onFloatingWidgetLongClick()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val layoutParams = floatingWidgetView.layoutParams as WindowManager.LayoutParams

                val x_cord = event.rawX.toInt()
                val y_cord = event.rawY.toInt()

                val x_cord_Destination: Int
                var y_cord_Destination: Int

                return when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        time_start = System.currentTimeMillis()

                        handler_longClick.postDelayed(runnable_longClick, 100)

                        remove_img_width = removeView.layoutParams.width
                        remove_img_height = removeView.layoutParams.height

                        x_init_cord = x_cord
                        y_init_cord = y_cord

                        x_init_margin = layoutParams.x
                        y_init_margin = layoutParams.y

                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        isLongClick = false
                        removeFloatingWidgetView.visibility = View.GONE
                        removeView.layoutParams.height = remove_img_height
                        removeView.layoutParams.width = remove_img_width
                        handler_longClick.removeCallbacks(runnable_longClick)

                        //If user drag and drop the floating widget view into remove view then stop the service
                        if (inBounded) {
                            stopSelf()
                            inBounded = false
                        }

                        //Get the difference between initial coordinate and current coordinate
                        val x_diff = x_cord - x_init_cord
                        val y_diff = y_cord - y_init_cord

                        //The check for x_diff <5 && y_diff< 5 because sometime elements moves a little while clicking.
                        //So that is click event.
                        if (abs(x_diff) < 5 && abs(y_diff) < 5) {
                            time_end = System.currentTimeMillis()
                            //Also check the difference between start time and end time should be less than 300ms
                            if (time_end - time_start < 300) onFloatingWidgetClick()
                        }

                        y_cord_Destination = y_init_margin + y_diff

                        val barHeight: Int = getStatusBarHeight()
                        if (y_cord_Destination < 0) {
                            y_cord_Destination = 0
                        } else if (y_cord_Destination + (floatingWidgetView.height + barHeight) > szWindow.y) {
                            y_cord_Destination =
                                szWindow.y - (floatingWidgetView.height + barHeight)
                        }

                        layoutParams.y = y_cord_Destination

                        inBounded = false

                        //reset position if user drags the floating view
                        if (!isViewExpanded()) {
                            resetPosition(x_cord)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isViewExpanded()) {
                            val x_diff_move = x_cord - x_init_cord
                            val y_diff_move = y_cord - y_init_cord

                            x_cord_Destination = x_init_margin + x_diff_move
                            y_cord_Destination = y_init_margin + y_diff_move

                            //If user long click the floating view, update remove view
                            if (isLongClick) {
                                val x_bound_left = szWindow.x / 2 - (remove_img_width * 1.5).toInt()
                                val x_bound_right =
                                    szWindow.x / 2 + (remove_img_width * 1.5).toInt()
                                val y_bound_top = szWindow.y - (remove_img_height * 1.5).toInt()
                                //If Floating view comes under Remove View update Window Manager
                                if (x_cord >= x_bound_left && x_cord <= x_bound_right && y_cord >= y_bound_top) {
                                    inBounded = true
                                    val x_cord_remove =
                                        ((szWindow.x - remove_img_height * 1.5) / 2).toInt()
                                    val y_cord_remove =
                                        (szWindow.y - (remove_img_width * 1.5 + getStatusBarHeight())).toInt()
                                    if (removeView.layoutParams.height == remove_img_height) {
                                        removeView.layoutParams.height =
                                            (remove_img_height * 1.5).toInt()
                                        removeView.layoutParams.width =
                                            (remove_img_width * 1.5).toInt()
                                        val param_remove =
                                            removeFloatingWidgetView.layoutParams as WindowManager.LayoutParams
                                        param_remove.x = x_cord_remove
                                        param_remove.y = y_cord_remove
                                        windowManager.updateViewLayout(
                                            removeFloatingWidgetView,
                                            param_remove
                                        )
                                        stopSelf()
                                    }
                                    layoutParams.x =
                                        x_cord_remove + abs(removeFloatingWidgetView.width - floatingWidgetView.width) / 2
                                    layoutParams.y =
                                        y_cord_remove + abs(removeFloatingWidgetView.height - floatingWidgetView.height) / 2
                                    //Update the layout with new X & Y coordinate
                                    windowManager.updateViewLayout(floatingWidgetView, layoutParams)
                                } else { //If Floating window gets out of the Remove view update Remove view again
                                    inBounded = false
                                    removeView.layoutParams.height = remove_img_height
                                    removeView.layoutParams.width = remove_img_width
                                }
                            }


                            layoutParams.x = x_cord_Destination
                            layoutParams.y = y_cord_Destination

                            //Update the layout with new X & Y coordinate
                            //Update the layout with new X & Y coordinate
                            windowManager.updateViewLayout(floatingWidgetView, layoutParams)
                        }
                        true
                    }
                    else -> {
                        false
                    }
                }
            }

        })
    }

    private fun implementClickListeners() {
        floatingWidgetView.findViewById<Button>(R.id.btn_hit).setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingWidgetView)
        windowManager.removeView(removeFloatingWidgetView)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_hit -> {
                counter++
                Toast.makeText(applicationContext, "Hit ($counter x)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /*  on Floating Widget Long Click, increase the size of remove view as it look like taking focus */
    private fun onFloatingWidgetLongClick() { //Get remove Floating view params
        val removeParams =
            removeFloatingWidgetView.layoutParams as WindowManager.LayoutParams
        //get x and y coordinates of remove view
        val x_cord = (szWindow.x - removeFloatingWidgetView.width) / 2
        val y_cord = szWindow.y - (removeFloatingWidgetView.height + getStatusBarHeight())
        removeParams.x = x_cord
        removeParams.y = y_cord
        //Update Remove view params
        windowManager.updateViewLayout(removeFloatingWidgetView, removeParams)
    }

    /*  Reset position of Floating Widget view on dragging  */
    private fun resetPosition(x_cord_now: Int) {
        moveToLeft(x_cord_now)
//        if (x_cord_now <= szWindow.x / 2) {
//            isLeft = true
//            moveToLeft(x_cord_now)
//        } else {
//            isLeft = false
//            moveToRight(x_cord_now)
//        }
    }


    /*  Method to move the Floating widget view to Left  */
    private fun moveToLeft(current_x_cord: Int) {
        val x = szWindow.x - current_x_cord
        object : CountDownTimer(500, 5) {
            //get params of Floating Widget view
            var mParams =
                floatingWidgetView.layoutParams as WindowManager.LayoutParams

            override fun onTick(t: Long) {
                val step = (500 - t) / 5
                mParams.x = 0 - (current_x_cord * current_x_cord * step).toInt()
                //If you want bounce effect uncomment below line and comment above line
                // mParams.x = 0 - (int) (double) bounceValue(step, x);
                //Update window manager for Floating Widget
                windowManager.updateViewLayout(floatingWidgetView, mParams)
            }

            override fun onFinish() {
                mParams.x = 0
                //Update window manager for Floating Widget
                windowManager.updateViewLayout(floatingWidgetView, mParams)
            }
        }.start()
    }

    /*  Method to move the Floating widget view to Right  */
    private fun moveToRight(current_x_cord: Int) {
        object : CountDownTimer(500, 5) {
            //get params of Floating Widget view
            var mParams =
                floatingWidgetView.layoutParams as WindowManager.LayoutParams

            override fun onTick(t: Long) {
                val step = (500 - t) / 5
                mParams.x =
                    (szWindow.x + current_x_cord * current_x_cord * step - floatingWidgetView.width).toInt()
                //  If you want bounce effect uncomment below line and comment above line
                //  mParams.x = szWindow.x + (int) (double) bounceValue(step, x_cord_now) - mFloatingWidgetView.getWidth();
                //  Update window manager for Floating Widget
                windowManager.updateViewLayout(floatingWidgetView, mParams)
            }

            override fun onFinish() {
                mParams.x = szWindow.x - floatingWidgetView.getWidth()
                //Update window manager for Floating Widget
                windowManager.updateViewLayout(floatingWidgetView, mParams)
            }
        }.start()
    }

    /*  Get Bounce value if you want to make bounce effect to your Floating Widget */
    private fun bounceValue(step: Long, scale: Long): Double {
        return scale * exp(-0.055 * step) * cos(0.08 * step)
    }

    private fun isViewExpanded(): Boolean {
        return floatingWidgetView.findViewById<FrameLayout>(R.id.expanded_container).visibility == View.VISIBLE
    }


    /*  return status bar height on basis of device display metrics  */
    private fun getStatusBarHeight(): Int {
        return ceil(25 * applicationContext.resources.displayMetrics.density.toDouble())
            .toInt()
    }


    /*  Update Floating Widget view coordinates on Configuration change  */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getWindowManagerDefaultDisplay()
        val layoutParams =
            floatingWidgetView.layoutParams as WindowManager.LayoutParams
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (layoutParams.y + (floatingWidgetView.height + getStatusBarHeight()) > szWindow.y) {
                layoutParams.y =
                    szWindow.y - (floatingWidgetView.height + getStatusBarHeight())
                windowManager.updateViewLayout(floatingWidgetView, layoutParams)
            }
            if (layoutParams.x != 0 && layoutParams.x < szWindow.x) {
                resetPosition(szWindow.x)
            }

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (layoutParams.x > szWindow.x) {
                resetPosition(szWindow.x)
            }
        }

        setExpandedSize()
    }

    private fun onFloatingWidgetClick() {
        if (isViewExpanded()) {
            expandedView.visibility = View.GONE
            counter = 0
        } else {
            expandedView.visibility = View.VISIBLE
        }
    }

    private fun setExpandedSize() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        val widthAfterCircle = width / 2
        val halfHeight = height / 2
        expandedView.layoutParams = LinearLayout.LayoutParams(widthAfterCircle, halfHeight)
    }
}