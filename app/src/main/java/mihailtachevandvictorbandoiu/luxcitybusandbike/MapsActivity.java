package mihailtachevandvictorbandoiu.luxcitybusandbike;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    LocationRequest mLocationRequest;
    String [] busList;
    //save for each stop its bus list
    HashMap<String,String[]> busListStop = new HashMap<String,String[]>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //check sdk version
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //set initial location
        mLastLocation = new Location("My location");
        mLastLocation.setLatitude(49.620181);
        mLastLocation.setLongitude(6.120503);

        //handling the nearest stop button click
        final Button nearestBus = (Button) findViewById(R.id.nearest);
        nearestBus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String stop = nearestStop("bus");
                String buses = getAllBuses(stop.split(";")[1],stop.split(";")[0]);
                //if no buses display none
                if(buses.equals("")) buses = "none";
                //once the stop details are taken navigate user to it
                LatLng nearestStop = new LatLng(Double.parseDouble(stop.split(";")[1].replace(",",".")), Double.parseDouble(stop.split(";")[0].replace(",",".")));
                mMap.addMarker(new MarkerOptions().position(nearestStop).title(stop.split(";")[3]).icon(BitmapDescriptorFactory.fromResource(R.drawable.bus)).snippet("Buses: " + buses));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nearestStop, 18.0f));
            }
        });

        //handling the nearest stop button click
        final Button nearestVeloh = (Button) findViewById(R.id.nearestVeloh);
        nearestVeloh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String stop = nearestStop("veloh");
                //once the stop details are taken navigate user to it
                LatLng nearestStop = new LatLng(Double.parseDouble(stop.split(",")[1]), Double.parseDouble(stop.split(",")[2]));
                mMap.addMarker(new MarkerOptions().position(nearestStop).title(stop.split(",")[0]).icon(BitmapDescriptorFactory.fromResource(R.drawable.bike)).snippet("Available bikes: " + stop.split(",")[3]));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nearestStop, 18.0f));
            }
        });
        //slider for distance to stops
        final SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar1);
        final TextView textView = (TextView) findViewById(R.id.textView1);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            int progress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progress = progresValue;
                mMap.clear();
                listAllVelohStations(String.valueOf(progresValue));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                textView.setText("Showing stops within: " + progress + " meters");
            }
        });

        //switch bus or veloh mode
        final ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton1);
        toggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(toggle.isChecked()){
                    mMap.clear();
                    long startTime = System.nanoTime();
                    listAllBusStations();
                    Toast.makeText(getApplicationContext(), "Bus mode on", Toast.LENGTH_LONG).show();
                    long estimatedTime = System.nanoTime() - startTime;
                    Log.d("time",String.valueOf(estimatedTime));
                }
                else{
                    mMap.clear();
                    listAllVelohStations("all");
                    Toast.makeText(getApplicationContext(), "Veloh mode on", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    //get all the bus data about a specific bus stop
    public String getAllBuses(String connectionString, String stop){
        String result = "";
        String str;
        try{
            //get all the buses currently at the particular stop
            connectionString = connectionString.replaceAll(" ", "%20");
            URL url = new URL("http://travelplanner.mobiliteit.lu/restproxy/departureBoard?accessId=cdt&" + connectionString.replace(";","").replace(" ","") +"&format=json");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            while ((str = in.readLine()) != null) {
                //there are buses going on
                if(!str.equals("{\"serverVersion\":\"1.0\",\"dialectVersion\":\"1.0\"}")){
                    //send it to the secondary activity on user click
                    busList = str.split("line");
                    busListStop.put(stop,busList);
                    for(int i = 1; i < busList.length; i++){
                        //no comma if it is the final element
                        if(i == busList.length-1) result += busList[i].substring(3).split("\"")[0];
                        else result += busList[i].substring(3).split("\"")[0] + ",";
                    }
                }
            }
            in.close();
        }catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            /*some bus stops like bouillon cannot extract data since it is not working in the same way as others
            the one on the website - http://travelplanner.mobiliteit.lu/restproxy/departureBoard?accessId=cdt&id=A=1@O=Belair,%20Sacr%C3%A9-Coeur@X=6,113204@Y=49,610279@U=82@L=200403005@B=1@p=1459856195&format=json
            however for Bouillon - http://travelplanner.mobiliteit.lu/restproxy/departureBoard?accessId=cdt&id=A=1@O=Hollerich,%20P+R%20Bouillon@X=6,107208@Y=49,599996@U=82@L=200415009@B=1@p=1478177594&format=json
            displays error
            */
            result = "cannot access api data";
        }
        return result;
    }

    //computing nearest bus or veloh stop with the API provided
    public String nearestStop(String string){
        String result = "";
        String str;
        double nearest = 999999999;
        String stopName;
        try {
            //take all the stations from the api
            if(string.equals("bus")){
                URL url = new URL("http://travelplanner.mobiliteit.lu/hafas/query.exe/dot?performLocating=2&tpl=stop2csv&look_maxdist=150000&look_x=6112550&look_y=49610700&stationProxy=yes.txt");
                //read all the text returned by the server
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                while ((str = in.readLine()) != null) {
                    //only the ones in LUX city according to https://en.wikipedia.org/wiki/Quarters_of_Luxembourg_City
                    if(str.contains("Beggen,") || str.contains("Belair,") || str.contains("Verlorenkost,") || str.contains("Bonnevoie,") ||
                            str.contains("Cents,") || str.contains("Cessange,") || str.contains("Clausen,") || str.contains("Dommeldange,") ||
                            str.contains("Eich,") || str.contains("Luxembourg,") || str.contains("Gasperich,") || str.contains("Grund,") ||
                            str.contains("Hamm,") || str.contains("Hollerich,") || str.contains("Kirchberg,") || str.contains("Limpertsberg,") ||
                            str.contains("Merl,") || str.contains("Muhlenbach,") || str.contains("Neudorf/Weimershof,") ||
                            str.contains("Centre,") || str.contains("Pfaffenthal,") || str.contains("Pulvermuhl,")
                            || str.contains("Rollingergrund,") || str.contains("Weimerskirch,") || str.contains("Luxembourg/Centre,")) {
                        Location stop = new Location("station");
                        stop.setLatitude(Double.parseDouble(str.split(";")[1].replace(",",".")));
                        stop.setLongitude(Double.parseDouble(str.split(";")[0].replace(",",".")));
                        //check distance for each stop and remember only the one with the shortest distance
                        if(mLastLocation.distanceTo(stop) < nearest){
                            nearest = mLastLocation.distanceTo(stop);
                            result = str;
                        }
                    }
                }
                in.close();
            }else{
                //URL url = new URL("https://developer.jcdecaux.com/rest/vls/stations/Luxembourg.csv");
                URL url = new URL("https://api.jcdecaux.com/vls/v1/stations?contract=Luxembourg&apiKey=96b9ee7224b03b6d262fe0be39c0c7645c9f714f");
                //read all the text returned by the server
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                double latitude, longitude = 0;
                int bikes = 0;
                while ((str = in.readLine()) != null) {
                    String stations [] = str.split("last_update");
                    for(int i = 0; i < stations.length - 1; i++){
                        stopName = stations[i].split("name")[1].split(",")[0].substring(3).replace("\"","");
                        bikes = Integer.parseInt(stations[i].split("available_bikes")[1].split(",")[0].substring(2));
                        Location stop = new Location("station");
                        latitude = Double.parseDouble(stations[i].split("lat")[1].split(",")[0].substring(2));
                        longitude = Double.parseDouble(stations[i].split("lng")[1].split(",")[0].substring(2).replace("}",""));
                        stop.setLatitude(latitude);
                        stop.setLongitude(longitude);
                        //check distance for each stop and remember only the one with the shortest distance
                        if(mLastLocation.distanceTo(stop) < nearest){
                            nearest = mLastLocation.distanceTo(stop);
                            result = stopName + "," + latitude + "," + longitude + "," + bikes;
                        }
                    }
                }
                in.close();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //list all the veloh stations with the bikes info on start up
        listAllVelohStations("all");

        //Add a marker in Lux and move the camera
        LatLng lux = new LatLng(49.611622, 6.131935);
        mMap.addMarker(new MarkerOptions().position(lux).title("Lux City"));
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(lux));
        //zooming at start at lux city
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lux, 14.0f));

        //enable zoom in and out
        mMap.getUiSettings().setZoomControlsEnabled(true);

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        //show the stop within particular range
        final EditText distance = (EditText) findViewById(R.id.distance);
        distance.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                //on entering the distance and pressing enter
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    //clear map and show only the ones that are in the specified distance
                    mMap.clear();
                    listAllVelohStations(distance.getText().toString());
                    return true;
                }
                return false;
            }
        });


        //clicking on info window which redirects to another activity for detailed information
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Toast.makeText(getApplicationContext(), "Info window clicked", Toast.LENGTH_SHORT).show();
                Bundle bundle = new Bundle();
                double longitude = marker.getPosition().longitude;
                //send veloh details
                if(marker.getSnippet().contains("bike")) bundle.putStringArray("data", new String []{"bike",
                        String.valueOf(marker.getPosition().latitude),String.valueOf(marker.getPosition().longitude)});
                //send bus details
                else bundle.putStringArray("data", busListStop.get(marker.getTitle()));
                Intent i = new Intent(getApplicationContext(),SecondaryActivity.class);
                i.putExtras(bundle);
                startActivity(i);
            }
        });
    }

    //list all the veloh stations within the distance
    public void listAllVelohStations(String distance){
        String str;
        String stopName;
        try{
            URL url = new URL("https://api.jcdecaux.com/vls/v1/stations?contract=Luxembourg&apiKey=96b9ee7224b03b6d262fe0be39c0c7645c9f714f");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            double latitude, longitude = 0;
            int bikes = 0;
            while ((str = in.readLine()) != null) {
                String stations [] = str.split("last_update");
                for(int i = 0; i < stations.length - 1; i++){
                    stopName = stations[i].split("name")[1].split(",")[0].substring(3).replace("\"","");
                    bikes = Integer.parseInt(stations[i].split("available_bikes")[1].split(",")[0].substring(2));
                    latitude = Double.parseDouble(stations[i].split("lat")[1].split(",")[0].substring(2));
                    longitude = Double.parseDouble(stations[i].split("lng")[1].split(",")[0].substring(2).replace("}",""));
                    //list all
                    if(distance.equals("all")){
                        LatLng stop = new LatLng(latitude, longitude);
                        mMap.addMarker(new MarkerOptions().position(stop).title(stopName).icon(BitmapDescriptorFactory.fromResource(R.drawable.bike)).snippet("Available bikes: " + bikes));
                    }else{
                        if(!distance.equals("")){
                            //list all station within the range
                            Location stop = new Location("station");
                            stop.setLatitude(latitude);
                            stop.setLongitude(longitude);
                            //check if the stop is in the given range
                            if(mLastLocation.distanceTo(stop) <= Double.parseDouble(distance)){
                                mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title(stopName).icon(BitmapDescriptorFactory.fromResource(R.drawable.bike)).snippet("Available bikes: " + bikes));
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()), 14.0f));
                            }
                        }
                    }
                }
            }
            in.close();
        }catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //list all the bus stations
    public void listAllBusStations(){
        String str;
        String stopName;
        try{
            URL url = new URL("http://travelplanner.mobiliteit.lu/hafas/query.exe/dot?performLocating=2&tpl=stop2csv&look_maxdist=150000&look_x=6112550&look_y=49610700&stationProxy=yes");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            while ((str = in.readLine()) != null) {
                //only the ones in LUX city according to https://en.wikipedia.org/wiki/Quarters_of_Luxembourg_City
                if(str.contains("Beggen,") || str.contains("Belair,") || str.contains("Verlorenkost,") || str.contains("Bonnevoie,") ||
                        str.contains("Cents,") || str.contains("Cessange,") || str.contains("Clausen,") || str.contains("Dommeldange,") ||
                        str.contains("Eich,") || str.contains("Luxembourg,") || str.contains("Gasperich,") || str.contains("Grund,") ||
                        str.contains("Hamm,") || str.contains("Hollerich,") || str.contains("Kirchberg,") || str.contains("Limpertsberg,") ||
                        str.contains("Merl,") || str.contains("Muhlenbach,") || str.contains("Neudorf/Weimershof,") ||
                        str.contains("Centre,") || str.contains("Pfaffenthal,") || str.contains("Pulvermuhl,")
                        || str.contains("Rollingergrund,") || str.contains("Weimerskirch,") || str.contains("Luxembourg/Centre,")) {
                    stopName = str.split("O")[1].substring(1).split("@")[0];
                    String buses = getAllBuses(str,stopName);
                    if(buses.equals("")) buses = "none";
                    LatLng stop = new LatLng(Double.parseDouble(str.split("Y=")[1].split("@")[0].replace(",",".")),
                            Double.parseDouble(str.split("X=")[1].split("@")[0].replace(",",".")));
                    mMap.addMarker(new MarkerOptions().position(stop).title(stopName).icon(BitmapDescriptorFactory.fromResource(R.drawable.bus)).snippet("Buses: " + buses));
                }
            }
            in.close();
        }catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        //location related code
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(getApplicationContext(), "Connection suspended!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLocationChanged(Location location) {

        mLastLocation = location;
        if (mCurrLocationMarker != null) {
            mCurrLocationMarker.remove();
        }

        //place current location marker
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        mCurrLocationMarker = mMap.addMarker(markerOptions);

        //move map camera
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

        //stop location updates
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(getApplicationContext(), "Connection failed!", Toast.LENGTH_LONG).show();
    }

    //handling the lifecycles
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //check permissions
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //clarification needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                //prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            } else {
                //no explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    //handling request result
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                //granted
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    //denied
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }
}