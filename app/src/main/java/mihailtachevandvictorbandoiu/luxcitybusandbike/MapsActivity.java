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
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

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
                mMap.addMarker(new MarkerOptions().position(nearestStop).title(stop.split(";")[3].split(",")[1].substring(1)).icon(BitmapDescriptorFactory.fromResource(R.drawable.bus)).snippet("Buses: " + buses));
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
    }

    //latitude and longitude are unique
    public String getAllBuses(String latitude, String longitude){
        String result = "";
        String str;
        String connectionString = "";
        try{
            //find connection string of the given stop
            URL url = new URL("http://travelplanner.mobiliteit.lu/hafas/query.exe/dot?performLocating=2&tpl=stop2csv&look_maxdist=150000&look_x=6112550&look_y=49610700&stationProxy=yes");
            //read all the text returned by the server
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            while ((str = in.readLine()) != null) {
                if(str.contains(latitude) && str.contains(longitude)){
                    connectionString = str; break;
                }
            }
            //get all the buses currently at the particular stop
            connectionString = connectionString.replaceAll(" ", "%20");
            url = new URL("http://travelplanner.mobiliteit.lu/restproxy/departureBoard?accessId=cdt&" + connectionString.replace(";","").replace(" ","") +"&format=json");
            in = new BufferedReader(new InputStreamReader(url.openStream()));
            while ((str = in.readLine()) != null) {
                //there are buses going on
                if(!str.equals("{\"serverVersion\":\"1.0\",\"dialectVersion\":\"1.0\"}")){
                    //send it to the secondary activity on user click
                    busList = str.split("Product");
                    for(int i = 1; i < busList.length; i++){
                        //no comma if it is the final element
                        if(i == busList.length-1) result += busList[i].split("name")[1].substring(6).split("\"")[0].trim();
                        else result += busList[i].split("name")[1].substring(6).split("\"")[0].trim() + ",";
                    }
                }
            }
            in.close();
        }catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
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
                    if(str.contains("Beggen") || str.contains("Belair") || str.contains("Verlorenkost") || str.contains("Bonnevoie") ||
                            str.contains("Cents") || str.contains("Cessange") || str.contains("Clausen") || str.contains("Dommeldange") ||
                            str.contains("Eich") || str.contains("Luxembourg") || str.contains("Gasperich") || str.contains("Grund") ||
                            str.contains("Hamm") || str.contains("Hollerich") || str.contains("Kirchberg") || str.contains("Limpertsberg") ||
                            str.contains("Merl") || str.contains("Muhlenbach") || str.contains("Neudorf/Weimershof") || str.contains("Centre") ||
                            str.contains("Pfaffenthal") || str.contains("Pulvermuhl") || str.contains("Rollingergrund") || str.contains("Weimerskirch")){
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
                if(marker.getSnippet().contains("bike")) bundle.putStringArray("data", new String []{
                        String.valueOf(marker.getPosition().latitude),String.valueOf(marker.getPosition().longitude)});
                //send bus details
                else bundle.putStringArray("data", busList);
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