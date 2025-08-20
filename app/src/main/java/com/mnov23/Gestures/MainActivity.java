/* launch activity */

package com.mnov23.Gestures;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.media.SoundPool;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Vibrator;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.media.MediaPlayer;
import android.media.AudioAttributes;

import com.thebluealliance.spectrum.SpectrumDialog;

import com.mnov23.Gestures.provider.SchemeShapes;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private String selectedShapeDrawing = "Circle";
    // used to access the app bar menu icons
    private Menu myMenu = null;

    private NewShape newShape;
    private EditDeleteShape editDeleteShape;
    private ViewShapes viewShapes;

    private ContentResolver resolver;

    private int selectedColor = -1; //-1 = no selected colour
    private String[] arColorsNames;
    private int[] arColorsValues;

    // New vars
    // Sensors Vibrators
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Vibrator vibrator;


    // Sound effects components
    private SoundPool soundPool;
    private int deleteSound1, deleteSound2, deleteSound3;
    private int soundsLoaded = 0;
    private boolean allSoundsReady = false;


    // Fling detection variables
    private static final float FLING_THRESHOLD = 16.0f;  // Increased from 12.0f - requires stronger fling   // 20.0f too strong. currently 16.0f
    private static final float VERTICAL_TOLERANCE = 6.0f;  // Tolerance for vertical position
    private static final long FLING_COOLDOWN = 1000;  // 2 seconds cooldown between flings  (2000), consider using 1 sec for audio overlap. (1000)
    // currently FLING_COOLDOWN is set to 850, which is less than 1 second (1000) to ensure that the audio overlaps are possible. (up to 6 channels max).

    private long lastFlingTime = 0;
    private boolean isPhoneVertical = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int width, height;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // sensors added
        initializeSensors();

        // Initialize sound effects
        initializeSounds();

        // to be used by delete shapes methods
        resolver = getApplicationContext().getContentResolver();


        arColorsNames = getResources().getStringArray(R.array.colorNames);
        arColorsValues = getResources().getIntArray(R.array.colorValues);

        if (selectedColor == -1)
            selectedColor = ContextCompat.getColor(this, R.color.md_blue_500);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //let's get the size of the display so we can set the size of the top FrameLayout to be 40% of this height
        //the status, app bar and bottom FrameLayout will take up the rest of the screen
        //the bottom FrameLayout will take up the rest of its RelativeLayout parent which it shares with
        //the top frame layout using match_parent
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;
        height = size.y;

        //now let the top FrameLayout's RelativeLayout parent size it
        FrameLayout frame = (FrameLayout) findViewById(R.id.fragment_top);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, (int) (height * 0.4));
        frame.setLayoutParams(lp);

        //lets instantiate all the fragment instances now to be efficient
        //viewShape and newShape will be needed immediately for the top and bottom FrameLayouts respectively
        //the other two will be needed when the option menu items are selected (see onOptionsItemSelected below)
        viewShapes = new ViewShapes();
        newShape = new NewShape();

        //Save the default drawing shape
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("selectedShapeDrawing", selectedShapeDrawing).apply();

        editDeleteShape = new EditDeleteShape();

        // Add the fragments to their parent 'fragment_container' FrameLayout
        //
        getSupportFragmentManager().beginTransaction()
           .add(R.id.fragment_top, viewShapes, "viewFragment")
           .addToBackStack("viewFragment")
           .commit();

        getSupportFragmentManager().beginTransaction()
           .add(R.id.fragment_bottom, newShape, "addFragment")
           .commit();
    }

    private void initializeSounds() {
        // Create SoundPool for playing short sound effects with overlapping capability
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(6)  // Increased to allow overlapping "Sakujo" sounds
                .setAudioAttributes(audioAttributes)
                .build();

        // Load all 3 "Sakujo" delete sound effects from res/raw/
        // File durations: 1sec, 1sec, 2sec respectively
        // - delete_cut1sec_soft.wav (1 second - Sakujo variation 1)
        // - delete_cut1sec.wav (1 second - Sakujo variation 2)
        // - delete_cut2sec_assassinate.wav (2 seconds - Sakujo variation 3)
        try {
            deleteSound1 = soundPool.load(this, R.raw.delete_cut1sec_soft, 1);
            deleteSound2 = soundPool.load(this, R.raw.delete_cut1sec, 1);
            deleteSound3 = soundPool.load(this, R.raw.delete_cut2sec_assassinate, 1);
        } catch (Exception e) {
            // If loading fails, we'll use MediaPlayer as backup
            allSoundsReady = false;
        }

        // Track when all sounds are loaded
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    soundsLoaded++;
                    if (soundsLoaded >= 3) {
                        allSoundsReady = true;
                    }
                }
            }
        });
    }



    // initialize newly added Sensors
    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                // Register sensor listener
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    

    //OPTIONS MENU STUFF AND RELATED METHODS
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.myMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.add_shape) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_bottom, newShape, "addFragment").commit();
            return true;
        } else if (id == R.id.edit_delete_shape) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_bottom, editDeleteShape, "editDeleteFragment").addToBackStack("editDeleteFragment").commit();
            return true;
        } else if (id == R.id.draw_circle) {
            selectedShapeDrawing = "Circle";
            myMenu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_circle_white, null));
            myMenu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_square_black, null));
            myMenu.getItem(2).setIcon(getResources().getDrawable(R.drawable.ic_line_black, null));
        } else if (id == R.id.draw_rectangle) {
            selectedShapeDrawing = "Rectangle";
            myMenu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_circle_black, null));
            myMenu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_square_white, null));
            myMenu.getItem(2).setIcon(getResources().getDrawable(R.drawable.ic_line_black, null));
        } else if (id == R.id.draw_line) {
            selectedShapeDrawing = "Line";
            myMenu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_circle_black, null));
            myMenu.getItem(1).setIcon(getResources().getDrawable(R.drawable.ic_square_black, null));
            myMenu.getItem(2).setIcon(getResources().getDrawable(R.drawable.ic_line_white, null));
        } else if (id == R.id.delete_all) {
            deleteAllShapes();
        } else if (id == R.id.show_Color_selector) {
            showSelectColor();
        } else {
            return super.onOptionsItemSelected(item);
        }
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("selectedShapeDrawing", selectedShapeDrawing).apply();

        return true;
    }


    private void deleteAllShapes() {
        resolver.delete(SchemeShapes.Shape.CONTENT_URI, null, null);
    }

    public void showSelectColor() {
        new SpectrumDialog.Builder(this)
                .setColors(R.array.demo_colors)
                .setSelectedColor(selectedColor)
                .setDismissOnColorSelected(true)
                .setOutlineWidth(2)
                .setOnColorSelectedListener(new SpectrumDialog.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(boolean positiveResult, @ColorInt int color) {
                        if (positiveResult) {
                            selectedColor = color;
                            //save the selected color to be used by ViewShapes (also set by NewShape)
                            getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("selectColor", selectedColor).apply();

                            //by the color value, get the name of the color, which is the name of the color picker icon
                            String colorName = getColorName(selectedColor);
                            //change the icon based on the color
                            myMenu.getItem(3).setIcon(getResources().getIdentifier(colorName, "drawable", getPackageName()));
                        }
                    }
                }).build().show(this.getSupportFragmentManager(), "");
    }

    private String getColorName(int color) {
        String colorName = null;
        int index = 0;
        while (arColorsValues[index] != color)
            index++;

        colorName = arColorsNames[index];
        return colorName;
    }


    // new methods Sensor Event Handling
    /**
     * @param sensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = sensorEvent.values[0];  // Left-Right movement
            float y = sensorEvent.values[1];  // Up-Down movement   (the intended orbit/axis) !!
            float z = sensorEvent.values[2];  // Forward-Backward movement
            /*
                hold phone normally, vertically in your hand, screen facing you (like reading)
                sharp wrist flick downwards, like as if you were:
                - shaking down a quicksilver thermometer to reset it
                - making a Wii controller downwards motion trying to accidentally destroy the TV screen
                - cracking a whip with your fist (in which you are holding the phone)
                - flicking water off your hand after visiting the bathroom WC full of rude people demanding the pissoir for themselves

                the motion should be quick and sharp,
                along the phone's length (top to bottom)
                while holding the phone in a vertical position facing you
             */

            // Check if phone is vertical (screen facing towards user, as if they were reading or watching the smartphone)
            // relaxed tolerance 6.0f
            isPhoneVertical = (Math.abs(z) < VERTICAL_TOLERANCE) && (Math.abs(x) < VERTICAL_TOLERANCE);

            // Only detect fling if phone is vertical
            if (isPhoneVertical) {
                detectVerticalFling(y);
            }
        }

        // damn Attack on Titan's 3D Vertical maneuvering equipment ODM
        // Sasageyo! Sasageyo! Shinzo wo Sasageyo!
        // Susumu beki mirai wo  sono te de kirihirake!
        // Susume!!!!!

        /* Levi simplified Chinese ASCII art ... if not displaying properly, check for UTF-8 or just Google it.
鑓塵幗膂蓿f寥寢膃暠瘉甅甃槊槎f碣綮瘋聟碯颱亦尓㍍i:i:i;;:;:: : :
澣幗嶌塹傴嫩榛畝皋i袍耘蚌紕欒儼巓襴踟篁f罵f亦尓㍍i:i:i;;:;:: : :
漲蔭甃縟諛f麭窶膩I嶮薤篝爰曷樔黎㌢´　　｀ⅷ踟亦尓㍍i:i:i;;:;:: : :
蔕漓滿f蕓蟇踴f歙艇艀裲f睚鳫巓襴骸　　　　　贒憊亦尓㍍i:i:i;;:;:: : :
榊甃齊爰f懈橈燗殪幢緻I翰儂樔黎夢'”　 　 ,ｨ傾篩縒亦尓㍍i:i:i;;:;:: : :
箋聚蜚壊劑薯i暹盥皋袍i耘蚌紕偸′　　　 雫寬I爰曷f亦尓㍍i:i:i;;:;:: : :
銕颱麼寰篝螂徑悗f篝嚠篩i縒縡齢　　 　 　 Ⅷ辨f篝I鋗f亦尓㍍i:i:i;;:; : : .
碯聟f綴麼辨螢f璟輯駲f迯瓲i軌帶′　　　　　`守I厖孩f奎亦尓㍍i:i:i;;:;:: : : .
綮誣撒f曷磔瑩德f幢儂儼巓襴緲′　 　 　 　 　 `守枢i磬廛i亦尓㍍i:i:i;;:;:: : : .
慫寫廠徑悗緞f篝嚠篩I縒縡夢'´　　　 　 　 　 　 　 `守峽f徑悗f亦尓㍍i:i:i;;:;:: : : .
廛僵I數畝篥I熾龍蚌紕襴緲′　　　　　　　　　　　　　‘守畝皋弊i劍亦尓㍍i:i:i;;:;:: : : .
瘧i槲瑩f枢篝磬曷f瓲軌揄′　　　　　　　　　　　　　,gf毯綴徑悗嚠迩忙亦尓㍍i:i:i;;:;::
襴罩硼f艇艀裲睚鳫襴鑿緲'　　　　　　　　　　 　 　 奪寔f厦傀揵猯i爾迩忙亦尓㍍i:i:
椈棘斐犀耋絎絲絨緲′　　　　　　 　 　 　 　 　 　 　 ”'罨悳萪f蒂渹幇f廏迩忙i亦尓㍍
潁樗I瘧德幢i儂巓緲′　　　　　　 　 　 　 　 　 　 r㎡℡〟”'罨椁裂滅楔滄愼愰迩忙亦
翦i磅艘溲I搦儼巓登zzz zzz㎜㎜ｧg　 　 緲 g　 　 甯體i爺ゎ｡, ”'罨琥焜毳徭i嵬塰慍絲
枢篝磬f曷迯i瓲軌f襴暹 甯幗緲 ,fi'　　 緲',纜｡　　贒i綟碕碚爺ゎ｡ ”'罨皴發傲亂I黹靱
緞愾慊嵬嵯欒儼巓襴驫 霤I緲 ,緲　　 ＂,纜穐　　甯絛跨飩i髢馳爺ゎ｡`'等誄I筴碌I畷
罩硼I蒻筵硺艇艀i裲睚亀 篳'’,緲　　g亀 Ⅶil齢　　贒罩硼i艇艀裲睚鳫爺靠飭蛸I裘裔
椈f棘豢跫跪I衙絎絲絨i爺i㎜iⅣ 　 ,緲i亀 Ⅶ靈,　　甯傅喩I揵揚惹屡絎痙棏敞裔筴敢
頬i鞏褂f跫詹雋髢i曷迯瓲軌霤 　 ,緲蔭穐 Ⅶ穐 　 讎椈i棘貅f斐犀耋f絎絲觚f覃黹黍
襴蔽戮貲艀舅I肅肄肆槿f蝓Ⅷ 　 緲$慚I穐,疊穐　 甯萪碾f鋗輜靠f誹臧鋩f褂跫詹i雋
         */
    }

    // vertical fling method
    private void detectVerticalFling(float y) {
        // Calculate total vertical acceleration
        float flickAcceleration = Math.abs(y);

        // Check if fling threshold is exceeded and cooldown period has passed
        long currentTime = System.currentTimeMillis();
        if (flickAcceleration  > FLING_THRESHOLD &&
                (currentTime - lastFlingTime) > FLING_COOLDOWN) {

            // Fling detected!
            onFlingDetected();
            lastFlingTime = currentTime;
        }
    }

    // the functionality I was looking for...
    // it goes here .. definitely into this crevice.
    private void onFlingDetected() {
        // Provide haptic feedback
        if (vibrator != null) {
            vibrator.vibrate(200); // Vibrate for 200ms
        }

        // Show toast notification
        Toast.makeText(this, "Fling detected! Deleting shapes...", Toast.LENGTH_SHORT).show();

        // Delete all shapes using your existing method
        deleteLastShape();

        // Play delete sound effect
        playDeleteSound();
    }

    // matching method to match ViewShapes new modified implementation of deleteLastShape()
    // deletes last Shape.
    private void deleteLastShape() {
        //ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                SchemeShapes.Shape.CONTENT_URI,
                SchemeShapes.Shape.PROJECTION,
                null, null,
                SchemeShapes.Shape.ID + " DESC LIMIT 1"
        );

        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int idIndex = getColumnIndexSafely(cursor, SchemeShapes.Shape.ID);
            String shapeId = cursor.getString(idIndex);

            resolver.delete(
                    SchemeShapes.Shape.CONTENT_URI,
                    SchemeShapes.Shape.ID + " = ? ",
                    new String[]{shapeId}
            );
            cursor.close();
        }
    }
    private int getColumnIndexSafely(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            throw new IllegalArgumentException("Column '" + columnName + "' not found in cursor");
        }
        return index;
    }

    private void playDeleteSound() {
        if (soundPool != null && allSoundsReady) {
            // Randomly select one of the 3 "Sakujo" sounds
            int randomSound = (int) (Math.random() * 3) + 1;
            int selectedSound;

            switch (randomSound) {
                case 1:
                    selectedSound = deleteSound1; // 1 sec Sakujo variation default
                    break;
                case 2:
                    selectedSound = deleteSound2; // 1 sec Sakujo variation crappier ver.
                    break;
                case 3:
                    selectedSound = deleteSound3; // 2 sec Sakujo variation w assassination fx
                    break;
                default:
                    selectedSound = deleteSound1; // fallback default
                    break;
            }

            // Play the randomly selected "Sakujo" sound
            // With 1-second cooldown and maxStreams=6, sounds can overlap beautifully
            // Edgy anime character can say "Sakujo" multiple times simultaneously for dramatic effect!
            soundPool.play(selectedSound, 0.8f, 0.8f, 1, 0, 1.0f);

        } else {
            // Fallback: Use system notification sound if custom sounds not ready
            try {
                MediaPlayer mediaPlayer = MediaPlayer.create(this,
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(0.3f, 0.3f); // Lower volume
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.release();
                        }
                    });
                }
            } catch (Exception e) {
                // Silent failure - no sound if both methods fail
            }
        }
    }

    /**
     * @param sensor
     * @param i
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // autogenerated stub
    }

    // additional sensorManagers
    @Override
    protected void onResume() {
        super.onResume();
        // Re-register sensor listener when activity resumes
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        // Unregister sensor listener to save battery when activity is paused
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up sensor resources
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}



/*
* Swipe gesture activities vary based on context. The speed at which a gesture is performed is the primary distinction between Drag, Swipe, and Fling.

Drag: Fine gesture, slower, more controlled, typically has an on-screen target
Swipe: Gross gesture, faster, typically has no on-screen target
Fling: Gross gesture, with no on-screen target
Gesture velocity impacts whether the action is immediately reversible.

A swipe becomes a fling based on ending velocity and whether the affected element has crossed a threshold (or point past which an action can be undone).
A drag maintains contact with an element, so reversing the direction of the gesture will drag the element back across the threshold.
A fling moves at a faster speed and removes contact with the element while it crosses the threshold, preventing the action from being undone.
*/


