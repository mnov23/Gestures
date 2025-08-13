/*

 */
package com.mnov23.Gestures;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
// deprecated
//import android.support.v4.app.Fragment;
//import android.support.v4.app.LoaderManager;
//import android.support.v4.content.CursorLoader;
//import android.support.v4.content.Loader;
//import android.support.v4.view.MotionEventCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.core.view.MotionEventCompat;


// cont.
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;

import com.mnov23.Gestures.provider.SchemeShapes;
import com.mnov23.Gestures.provider.ShapeValues;

import static com.mnov23.Gestures.provider.SchemeShapes.Shape;

/**
 * A simple {@link Fragment} subclass.
 */
public class ViewShapes extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String DEBUG_TAG = "Gestures";

    private int mLastTouchX;
    private int mLastTouchY;
    private ContentResolver resolver;

    public static CustomView customView = null;

    //gesture stuff
    private GestureDetector mDetector;
    private ScaleGestureDetector mScaleDetector;

    //interference flags
    private boolean isLongAndDrag = false;
    private boolean isOnScroll = false;
    private boolean isOnScale = false;

    private boolean singletap = false;
    private SensorManager mSensorManager;
    private Sensor mSensor;


    //other
    String selectedShapeDrawing = "Circle"; //default

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
        // setting the sensors up
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        resolver = getActivity().getContentResolver(); //***
    }


    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];


            // X axis values are supposed to be bigger than 6 or less than -6
            // Y axis values are supposed to be bigger than 4 or less than -4

            System.out.println("X: " + x);
            System.out.println("Y: " + y);
            System.out.println("Z: " + z);

            if(x > 6 || x < -6) {
                System.out.println("Perform delete action");
                deleteLastShape();
            }
            if(y > 4 || y < -4) {
                System.out.println("Perform colour cycle action");
                if(y > 3) {
                    System.out.println("Cycle forward");
                    updateLastShape((int)y);
                }
                if(y < -3) {
                    System.out.println("Cycle backwards");
                    updateLastShape((int)y);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        customView = new CustomView(getContext());
        mDetector = new GestureDetector(getContext(), new MyGestureListener());
        mScaleDetector = new ScaleGestureDetector(getContext(), new myScaleListener());

        //***
        customView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                //Circle is the default shape if can't find the key
                selectedShapeDrawing = getActivity().getSharedPreferences("settings", Context.MODE_PRIVATE).getString("selectedShapeDrawing", "Circle");
                int x = (int) ev.getX(); int y = (int) ev.getY();
                int dX = 0, dY = 0;
                int dots = 0;


                //"Note that MotionEventCompat is not a replacement for the MotionEvent class. Rather,
                //it provides static utility methods to which you pass your MotionEvent object in order
                //to receive the desired action associated with that event."
                int action = MotionEventCompat.getActionMasked(ev);
                switch (action){
                    case MotionEvent.ACTION_MOVE:
                        //this event is used to move last drawn shape after long press
                        //OnScroll is not fired after a long press
                        if (isLongAndDrag) { //set in
                            updateLastShape(x, y, -1, -1, -1, false); //-1 value unchanged, false means no pinch scaling required
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        //Log.d(DEBUG_TAG, "up ");
                        //onSingleTapUp cannot be used here to reset flags as it only occurs
                        //after an isolated single tap not after a long drag or scroll

                        //reset two flags, if anything was in progress (e.g. long drag, scroll) an up will kill it
                        isLongAndDrag = false;
                        isOnScroll = false;
                        break;
                    case MotionEvent.ACTION_DOWN:
                        // DeleteAllShapes
                        // top left corner
                        // and bottom right corner
                        if(x < 150 && y < 150)
                        {
                            dX = x; dY = y;
                            singletap = true;
                        }
                        if(x > (v.getWidth() - 150) && y > (v.getHeight() - 150))
                        {
                            if(dX < 150 && dY < 150 && singletap)
                            {
                                resolver.delete(SchemeShapes.Shape.CONTENT_URI, null, null);
                                singletap = false;
                            }
                        }
                        if( (x > 150 && y > 150) && /* condition 1 */
                                (x < (v.getWidth() - 150) && y < (v.getHeight() - 150) /* condition 2 */)
                                ){
                            singletap = false;
                        }
                        break;
                }

                //pass all MotionEvent to Gesture Detectors for them to decide if some combination of MotionEvents is a Gesture or not
                mDetector.onTouchEvent(ev);
                mScaleDetector.onTouchEvent(ev);

                return true; //event handled no need to pass it on for further handling
            }
        });
        //***

        return (customView);
    }


    private void storeShape(String shape, int x, int y, int deltaX, int deltaY) {
        int selectedColor = getActivity().getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("selectColor", 0);

        ContentValues contentValues = new ContentValues();
        contentValues.put(SchemeShapes.Shape.SHAPE_TYPE, shape);
        contentValues.put(SchemeShapes.Shape.SHAPE_X, x);
        contentValues.put(SchemeShapes.Shape.SHAPE_Y, y);
        contentValues.put(SchemeShapes.Shape.SHAPE_RADIUS, Math.max(deltaX, deltaY));
        contentValues.put(SchemeShapes.Shape.SHAPE_WIDTH, deltaX);
        contentValues.put(SchemeShapes.Shape.SHAPE_HEIGHT, deltaY);
        contentValues.put(SchemeShapes.Shape.SHAPE_BORDER_THICKNESS, 10);
        contentValues.put(SchemeShapes.Shape.SHAPE_COLOR, selectedColor);
        resolver.insert(SchemeShapes.Shape.CONTENT_URI, contentValues);
    }
    //***

    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {

        CursorLoader cursorLoader = new CursorLoader(getActivity(),
            Shape.CONTENT_URI,
            //VersionContract.Version.buildUri(2),
            Shape.PROJECTION,
            null,
            null,
            null
        );
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        ShapeValues[] shapes = new ShapeValues[cursor.getCount()];
        int i = 0;
        if (cursor.moveToFirst()) {
            do {

                shapes[i] = new ShapeValues(cursor.getString(
                    cursor.getColumnIndex(Shape.SHAPE_TYPE)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_X)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_Y)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_BORDER_THICKNESS)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_RADIUS)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_WIDTH)),
                    cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_HEIGHT)),
                    cursor.getString(cursor.getColumnIndex(Shape.SHAPE_COLOR))
                );
                i++;
                // do what ever you want here
            } while (cursor.moveToNext());
        }
        // cursor.close();
        customView.numberShapes = cursor.getCount();
        customView.shapes = shapes;
        customView.invalidate();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //do your stuff for your fragment here
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        //    simpleCursorAdapter.swapCursor(null);
    }

    //GESTURE STUFF
    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if(!isOnScale && e1.getPointerCount() == 1 && e2.getPointerCount() == 1) { // if there is no pinching ongoing right now
                //Log.d(DEBUG_TAG, "onScroll " + selectedShapeDrawing);
                int x1, y1, x2, y2;
                int dX, dY;

                x1 = (int) e1.getX(); y1 = (int) e1.getY();
                x2 = (int) e2.getX(); y2 = (int) e2.getY();

                dX = x2 - x1; dY = y2 - y1;


                if (!selectedShapeDrawing.equals("Line")) { //if the selected shape is not a freehand line
                    //following work for circles but not for other shapes, need fixing
                    dX = Math.abs(dX);
                    dY = Math.abs(dY);

                    if (!isOnScroll) { // if this is the first call, draw the selected shape
                        storeShape(selectedShapeDrawing, x1, y1, dX, dY);
                        isOnScroll = true;
                    } else { // if this is not first, and you are still onScroll (isOnScroll is still true), update the selected shape
                        updateLastShape(x1, y1, dX, dY, Math.max(dX, dY), false); //-1 no value change required, false no pinch scaling required
                    }
                }
                else { //if the selected shape is hand drawn line, draw the standard circle that represents a point
                    if (!isLongAndDrag) {
                        dX = 5;
                        dY = 5;
                        storeShape("Circle", x2, y2, dX, dY);
                    }
                }

            }
            return true;
        }

        // Not required any more, ACTION_UP replaces this callBack
//       @Override
//        public boolean onSingleTapUp(MotionEvent e) {
//            Log.d(DEBUG_TAG, "OnSingleTop");
//
//            return super.onSingleTapUp(e);
//        }

        //this callback, draws the standard shape for the selected shapes
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            storeShape(selectedShapeDrawing, (int) e.getX(), (int) e.getY(), 50, 50);
            //return super.onDoubleTap(e);
            return true;
        }

        // set a Long press flag to true to be used later by ACTION_MOVE
        @Override
        public void onLongPress(MotionEvent e) {
            //Log.d(DEBUG_TAG, "onLongPress");
            super.onLongPress(e);
            isLongAndDrag = true;
        }
    }

    private class myScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        float scale = 1.0f;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            //Log.d(DEBUG_TAG, "onScaleBegin ");
            isOnScale = true; // start pinching

            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //Log.d(DEBUG_TAG, "onScale " + detector.getScaleFactor());
            scale *= detector.getScaleFactor();

            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            //Log.d(DEBUG_TAG, "onScaleEnd " + scale);


            //convert form double to int
            //eg 1.5--> 150
            int scaleFactor = (int) (scale * 100);
            scale = 1.0f;         // reset the scale
            isOnScale = false; // done with pinching, lets OnScroll get some work

            /**
             * updateLastShape(x,y,width,height,radius)
             * -1 means no need to update and use the original value
             * last parameter is the scale flag which is used to perform the pinching event
             * scaleFactor will be applied on width and height or radius
             */
            updateLastShape(-1, -1, scaleFactor, scaleFactor, scaleFactor, true);
            super.onScaleEnd(detector);
        }
    }

    private void deleteLastShape()
    {
        Cursor cursor = getLastShape();
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            resolver.delete(SchemeShapes.Shape.CONTENT_URI, "_id=" + cursor.getInt(cursor.getColumnIndex(Shape.ID)), null);
        }
    }

    private void updateLastShape(int dir) {
        Cursor cursor = getLastShape();
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            ContentValues contentValues = new ContentValues();
            contentValues.put(SchemeShapes.Shape.SHAPE_TYPE, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_TYPE)));
            contentValues.put(Shape.SHAPE_X, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_X)));
            contentValues.put(Shape.SHAPE_Y, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_Y)));
            contentValues.put(Shape.SHAPE_RADIUS, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_RADIUS)));
            contentValues.put(Shape.SHAPE_WIDTH, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_WIDTH)));
            contentValues.put(Shape.SHAPE_HEIGHT, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_HEIGHT)));
            contentValues.put(SchemeShapes.Shape.SHAPE_BORDER_THICKNESS, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_BORDER_THICKNESS)));

            if(dir > 0) {
                // change colour
                contentValues.put(SchemeShapes.Shape.SHAPE_COLOR, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_COLOR)));
                // int selectedColor = getActivity().getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("selectColor", 0);
            }
            if(dir < 0 ) {
                // change colour
                contentValues.put(SchemeShapes.Shape.SHAPE_COLOR, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_COLOR)));
                // int selectedColor = getActivity().getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("selectColor", 0);

            }

            resolver.update(SchemeShapes.Shape.CONTENT_URI, contentValues, "_id=" + cursor.getInt(cursor.getColumnIndex(Shape.ID)), null);
        }
    }

    private void updateLastShape(int x, int y, int width, int height, int radius, boolean scaleFlag) {
        Cursor cursor = getLastShape();
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            ContentValues contentValues = new ContentValues();
            contentValues.put(SchemeShapes.Shape.SHAPE_TYPE, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_TYPE)));

            if (x != -1) {
                contentValues.put(Shape.SHAPE_X, x);
            }
            else
                contentValues.put(Shape.SHAPE_X, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_X)));

            if (y != -1)
                contentValues.put(Shape.SHAPE_Y, y);
            else
                contentValues.put(Shape.SHAPE_Y, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_Y)));

            if (scaleFlag)
                contentValues.put(Shape.SHAPE_RADIUS, (radius / 100.0) * cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_RADIUS)));
            else if (radius != -1)
                contentValues.put(Shape.SHAPE_RADIUS, radius);
            else

                contentValues.put(Shape.SHAPE_RADIUS, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_RADIUS)));


            if (width != -1)
                contentValues.put(Shape.SHAPE_WIDTH, width);
            else if (scaleFlag)
                contentValues.put(Shape.SHAPE_WIDTH, (width / 100.0) * cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_WIDTH)));
            else
                contentValues.put(Shape.SHAPE_WIDTH, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_WIDTH)));

            if (height != -1)
                contentValues.put(Shape.SHAPE_HEIGHT, height);
            else if (scaleFlag)
                contentValues.put(Shape.SHAPE_HEIGHT, (height / 100.0) * cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_WIDTH)));
            else
                contentValues.put(Shape.SHAPE_HEIGHT, cursor.getInt(cursor.getColumnIndex(Shape.SHAPE_HEIGHT)));

            contentValues.put(SchemeShapes.Shape.SHAPE_BORDER_THICKNESS, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_BORDER_THICKNESS)));
            contentValues.put(SchemeShapes.Shape.SHAPE_COLOR, cursor.getString(cursor.getColumnIndex(Shape.SHAPE_COLOR)));
            resolver.update(SchemeShapes.Shape.CONTENT_URI, contentValues, "_id=" + cursor.getInt(cursor.getColumnIndex(Shape.ID)), null);
        }
    }

    public Cursor getLastShape() {
        Cursor cursor = resolver.query(Shape.CONTENT_URI, Shape.PROJECTION, null, null, Shape.ID + " DESC LIMIT 1");
        return cursor;
    }

    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(sensorListener, mSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onStop() {
        super.onStop();
        mSensorManager.unregisterListener(sensorListener);
    }

}


