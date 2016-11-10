package mihailtachevandvictorbandoiu.luxcitybusandbike;

/**
 * Created by Michael on 31.10.2016 г..
 */

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

//own custom implementation for adapter class
public class CustomAdapter extends BaseAdapter{

    String [] result;
    Context context;
    boolean bus;
    private static LayoutInflater inflater = null;

    public CustomAdapter(SecondaryActivity secondaryActivity, String[] data, boolean bus) {

        String row = "";
        this.bus = bus;

        //veloh details
        if(data[0].contains(".")){
            this.bus = false;
            result = new String[1];
            String str;
            try{
                URL url = new URL("https://api.jcdecaux.com/vls/v1/stations?contract=Luxembourg&apiKey=96b9ee7224b03b6d262fe0be39c0c7645c9f714f");
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                double latitude, longitude = 0;
                int bikes = 0;
                while ((str = in.readLine()) != null) {
                    String stations [] = str.split("last_update");
                    for(int i = 0; i < stations.length - 1; i++){
                        //find only station with given location
                        if(stations[i].contains(data[0]) && stations[i].contains(data[1])){
                            //name
                            row += stations[i].split("name")[1].substring(3).split("\"")[0] + ", ";
                            //status
                            row += stations[i].split("status")[1].substring(3).split("\"")[0] + ", ";
                            //bike stands
                            row += stations[i].split("bike_stands")[1].substring(2).split(",")[0] + ", ";
                            //available bike stands
                            row += stations[i].split("available_bike_stands")[1].substring(2).split(",")[0] + ", ";
                            //available bikes
                            row += stations[i].split("available_bikes")[1].substring(2).split(",")[0];
                            result[0] = row;
                            break;
                        }
                    }
                }
                in.close();
            }catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            this.bus = true;
            //bus details
            result = new String [data.length - 1];
            //handling and parsing the data
            for(int i = 1; i < data.length; i++){
                //line
                row += data[i].split("name")[1].substring(6).split("\"")[0].trim() + ", ";
                //time
                row += data[i].split("time")[1].substring(3).split("\"")[0] + ", ";
                //direction
                row += data[i].split("direction")[1].substring(3).split("\"")[0] + ", ";
                //operator code, but not all have such code
                if(data[i].contains("operatorCode")) row += data[i].split("operatorCode")[1].substring(3).split("\"")[0];
                //populate the output array
                result[i - 1] = row;
                row = "";
            }
        }

        context = secondaryActivity;
        inflater = ( LayoutInflater )context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
    @Override
    public int getCount() {
        return result.length;
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public class Holder
    {
        TextView textView;
        ImageView img;
    }
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder = new Holder();
        View rowView;
        rowView = inflater.inflate(R.layout.activity_listview, null);
        holder.textView = (TextView) rowView.findViewById(R.id.textView);
        holder.img = (ImageView) rowView.findViewById(R.id.imageView);
        holder.textView.setText(result[position]);
        if(bus){
            Calendar now = Calendar.getInstance();
            Calendar busTime = Calendar.getInstance();
            String hour = result[position].split(",")[1].split(":")[0].trim();
            String min = result[position].split(":")[1];
            busTime.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DATE), Integer.valueOf(hour), Integer.valueOf(min));
            //red = bus is late, otherwise on time
            if(now.after(busTime)) holder.textView.setBackgroundColor(Color.RED);
            else holder.textView.setBackgroundColor(Color.GREEN);
        }
        if(bus)holder.img.setImageResource(R.drawable.bus);
        else holder.img.setImageResource(R.drawable.bike);
        rowView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(context, "You Clicked " + result[position], Toast.LENGTH_SHORT).show();
            }
        });
        return rowView;
    }

}