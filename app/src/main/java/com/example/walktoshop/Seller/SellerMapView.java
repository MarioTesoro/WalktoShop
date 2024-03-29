package com.example.walktoshop.Seller;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;


import com.example.walktoshop.Model.Business;
import com.example.walktoshop.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class SellerMapView extends AppCompatActivity implements OnMapReadyCallback {

    FirebaseFirestore db=FirebaseFirestore.getInstance();
    private GoogleMap mMap;
    private String UID = null;
    private static final String API_KEY = "AIzaSyBrbjgwm3CB6qBhWaa3cMrRV3Ek9XW0cPc";
    SearchView search;
    String location;
    boolean isExisting = false;
    List<LatLng> latLngs = new ArrayList<LatLng>();
    Business business=new Business();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_map_view);

        getSupportActionBar().setTitle(R.string.chose_business);

        //riceve l'intent contenente l'identificatore univoco dell'utente
        Intent intent = getIntent();
        if(intent.hasExtra("UID")){
            UID=intent.getStringExtra("UID");
            SellerMapView.this.business.setOwnerUID(UID);
        }

        //il getSupportFragment il fragment contenente la mappa, all'interno del layout  dell'activity
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        search = findViewById(R.id.search_bar);
        //inizializzazione delle places API
        if(!Places.isInitialized()){
            Places.initialize(getApplicationContext(),API_KEY);
        }
        PlacesClient client=Places.createClient(this);
        /**
         * viene settato un listener sulla SearchView che prende il nome della attività digitata dall'utente e utilizzando l'oggetto geodecoder
         * ottiene la latitudine e la longitudine dell'attività altrimenti se la stringa digitata è vuota, non esiste oppure è stata digitata un attività già esistente
         * verra mostrato un dialog che avvisa l'utente dell'errore commesso.
         */
        search.setIconified(false);
        search.setQueryHint(getResources().getString(R.string.queryHint));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                location=search.getQuery().toString();
                List<Address> addresses=null;
                if(location!=null || !location.trim().equals("")){
                    Geocoder geocoder=new Geocoder(SellerMapView.this);
                    try{
                        addresses=geocoder.getFromLocationName(location,1);
                        if(addresses.isEmpty()){
                            dialog();
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    if (addresses!=null && !addresses.isEmpty()){
                        Address addr=addresses.get(0);
                        verifyBusiness(addr);
                    }
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        mapFragment.getMapAsync(this);
    }

    /**
     * Usa l'oggetto di tipo Address contenente latitudine, longitudine e locality di un determinato luogo per fare una query al db
     * prendendo tutte le attività aventi come locality quella definita all'interno dell'oggetto di tipo Addresses, successivamente controlla
     * le latitudini e longitudini di queste attività estratte dal db con quella dell'oggetto di tipo  Address e verifica se l'attività digitata dall'utente
     * è già esistente o meno, nel caso in cui esista mostra all'utente un dialog che indica l'errore commesso, se invece è la prima volta che viene inserita, la mappa
     * esegue uno  zoom sul marker corrispondente a latitudine e longitudine contenute nell'oggetto di tipo Address.
     * @param addr
     */
    private void verifyBusiness(Address addr) {

        double latitude = addr.getLatitude();
        double longitude = addr.getLongitude();
        LatLng placeLatLng = new LatLng(latitude,longitude);
        String locality = addr.getLocality();

        db.collection("attivita").whereEqualTo("locality", locality).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for (QueryDocumentSnapshot document : task.getResult()){
                        double lat = Double.parseDouble(document.getString("latitude"));
                        double longt = Double.parseDouble(document.getString("longitude"));
                        Log.d("DB-PLACE", lat+"-"+longt);
                        if(latitude == lat && longitude == longt){
                            isExisting = true;
                            break;
                        }
                    }
                    if(isExisting == true){
                        AlertDialog.Builder builder = new AlertDialog.Builder(SellerMapView.this);
                        // Add the buttons
                        builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        }).setMessage(R.string.allert_business_exist);
                        builder.show();
                        isExisting = false;
                    } else {
                        Log.d("place",addr.getLatitude()+"--"+addr.getLongitude()+"--"+addr.getLocality());
                        LatLng place=new LatLng(addr.getLatitude(),addr.getLongitude());
                        mMap.addMarker(new MarkerOptions().position(place).title(location));
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place,15));
                        Toast.makeText(SellerMapView.this, R.string.addBusinessDialog, Toast.LENGTH_LONG).show();

                        /**
                         *   quando l'utente clicca sul marker viene caricato l'oggetto di tipo business con le informazioni relative a latitudine
                         *   longitudine, localita e business UID creato sommando la latitudine e la longitudine.
                         */

                        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                            @Override
                            public boolean onMarkerClick(Marker marker) {
                                if(SellerMapView.this.location.contains(",")){
                                    String[] res = SellerMapView.this.location.split("[,]", 0);
                                    SellerMapView.this.location = res[0];
                                }
                                SellerMapView.this.business.setName(SellerMapView.this.location);
                                SellerMapView.this.business.setLongitude(String.valueOf(addr.getLongitude()));
                                SellerMapView.this.business.setLatitude(String.valueOf(addr.getLatitude()));
                                SellerMapView.this.business.setLocality(addr.getLocality());
                                SellerMapView.this.business.setUID(calculateMyBusinessCustomUID(addr.getLatitude(),addr.getLongitude()));
                                setBusiness(SellerMapView.this.business);
                                return false;
                            }
                        });
                    }

                }
            }
        });
    }

    //in questo metodo viene settata la camera sull'italia
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng italy = new LatLng(43.06103001266056, 12.882105287940128);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(italy));
    }

    //il metodo riceve l'oggetto business in input ed esegue una query che setta l' attività corrispondente all' identificatore univoco del business
    private void setBusiness(Business business){

        db.collection("attivita").document(business.getUID()).set(this.business).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful()){
                    getSeller(business.getUID());
                }
            }
        });
    }

    /**
     * metodo che aggiunge l'attività del venditore sul db con un custom UID
     * @param businessCustomUID
     */
    private void getSeller(String businessCustomUID){
        db.collection("venditore").document(UID).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()){
                    DocumentSnapshot document = task.getResult();
                    ArrayList<String> businessUID = (ArrayList<String>) document.get("businessUID");
                    if(businessUID == null)
                    {
                        businessUID = new ArrayList<>();
                    }
                    businessUID.add(businessCustomUID);
                    Log.d("op",businessUID.toString());
                    updateSeller(businessUID);
                }
            }
        });
    }

    /**
     * L'array di attività viene sovrascritto sul db con un nuovo array contenente l'attività agguiunta
     * @param businessUID
     */
    private void updateSeller(ArrayList<String> businessUID){
        db.collection("venditore").document(UID).update("businessUID",businessUID).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful())
                {
                    finish();
                }
            }
        });
    }
    //dialog mostrato quando la stringa digitata è vuota oppure non è stata trovata
    private void dialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Add the buttons
        builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

            }
        }).setMessage(R.string.businessNotFound);
        // Set other d
        builder.show();
    }
    //viene calcolato l'UID del business appena digitato dall'utente come somma delle sue coordinate di latitudine e longitudine
    private String calculateMyBusinessCustomUID(Double latitude,Double longitude){
        if(latitude!=null && longitude!=null){
            String customUID=null;
            customUID= String.valueOf(latitude+longitude);
            customUID =customUID.replaceAll("[^0-9]", "");
            return customUID;
        }else{
            return null;
        }
    }
}
