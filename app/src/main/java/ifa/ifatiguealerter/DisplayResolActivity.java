package ifa.ifatiguealerter;

import android.graphics.Point;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;

public class DisplayResolActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_resol);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        try {
            WindowChangeDetectingService.writeToFile("Resolution:" + size.x + "x" + size.y + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        finish();
    }
}
