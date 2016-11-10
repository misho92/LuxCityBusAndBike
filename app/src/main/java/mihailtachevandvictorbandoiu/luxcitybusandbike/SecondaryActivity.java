package mihailtachevandvictorbandoiu.luxcitybusandbike;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;
import android.widget.TextView;

//secondary activity for showing bus details
public class SecondaryActivity extends AppCompatActivity {

    Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_secondary);
        context = this;
        Bundle bundle = this.getIntent().getExtras();
        boolean bus = false;
        ListView listView = (ListView) findViewById(R.id.buses);
        TextView textView = new TextView(getApplicationContext());
        String [] data = bundle.getStringArray("data");
        //veloh or bus
        if(bundle.getStringArray("data") != null) {
            if(bundle.getStringArray("data").length == 2) {
                textView.setText("Name,status,bike stands,available bike stands,available bikes");
            }
            else {
                textView.setText("\t Line,time,direction,operator code");
                bus = true;
            }
            textView.setTextSize(22);
            textView.setTypeface(null, Typeface.BOLD);
            listView.addHeaderView(textView);
            //passing data array to the custom adapter
            listView.setAdapter(new CustomAdapter(this, bundle.getStringArray("data"),bus));
        }
    }
}
