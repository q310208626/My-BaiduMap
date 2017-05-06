package com.lsj.hdmi.mybaidumap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.Poi;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.inner.Point;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.PushMessage;

import java.security.Permission;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static String TAG="MyBaiduMap_MainActivity";

    private MapView mapView;        //百度地图View
    private BaiduMap baiduMap;      //百度地图对象

    //定位服务
    private LocationClient mLocationClient;         //百度api location客户端控制pai服务
    public BDLocationListener myListener = new MyLocationListener();        //百度Api  location监听器获取并处理位置信息
    private MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL; //定位模式
    private BitmapDescriptor currentMarker;       //定位标志

    //鹰眼轨迹服务
    private long traceServiceId=140176;
    private String entityName = "myTrace";
    boolean isNeedObjectStorage = false;
    private Trace mTrace;
    private LBSTraceClient mTraceClient;


    Toast toast;
    String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION};    //gps权限

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menuitem_permission:
                    if (!checkPermission()){
                        requestGPSPermission();
                    }
                break;
            case R.id.menuitem_mylocation:
                    getMyLocation();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    //申请GPS权限后结果处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 0:
                if (grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    mLocationClient.start();
                }else{
                    toast.setText("GPS没有开启");
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mapView.onPause();
    }

    private void init() {
        toast=Toast.makeText(this,"",Toast.LENGTH_LONG);
        mapView = (MapView) findViewById(R.id.baidu_mapview);
        baiduMap = mapView.getMap();
        baiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);

        //初始化定位服务
        mLocationClient=new LocationClient(getApplicationContext());    //初始化轨迹服务端
        mLocationClient.registerLocationListener(myListener);           //注册位置监听器
        baiduMap.setMyLocationEnabled(true);
        initLocation();

        //初始化鹰眼服务
        mTrace = new Trace(traceServiceId, entityName, isNeedObjectStorage);
        mTraceClient= new LBSTraceClient(getApplicationContext());      //初始化轨迹服务端
        int gatherInterval = 3;                                     // 定位周期(单位:秒)
        int packInterval = 10;                                      // 打包回传周期(单位:秒)
        mTraceClient.setInterval(gatherInterval, packInterval);     // 设置定位和打包周期


    }

    //获取GPS权限
    private void requestGPSPermission(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //6.0以上权限获取
            requestPermissions(permissions, 0);
        }else {
            //6.0以下权限获取
        }
    }

    //检查GPS权限
    private boolean checkPermission(){
        int check=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION);
        Boolean hasPermission=false;
        hasPermission=check==PackageManager.PERMISSION_GRANTED;
        return hasPermission;
    }

    //开始实时监控位置
    private void startGetLocation(){
        if (isNetworkConnected()){

            if (checkPermission()){
                toast.setText("GPS已打开");
                toast.show();
                mLocationClient.start();
            }else {
                toast.setText("请打开GPS");
                toast.show();
               requestGPSPermission();
            }

        }else {
            toast.setText("网络不可用，请打开网络");
            toast.show();
        }
    }

    //在地图上获取我的位置，并显示在地图上
    private void getMyLocation(){
        if (checkPermission()){
            //如果有GPS权限
            BDLocation location=mLocationClient.getLastKnownLocation();
            baiduMap.setMyLocationEnabled(true);

            //定义地图更新信息
            LatLng latlng=new LatLng(location.getLatitude(),location.getLongitude());
            float rotate=0;
            float overlook=0;
            float zoom= 19;
            //地图中心点在地图上的坐标，这里去地图中心
            android.graphics.Point point=new android.graphics.Point(mapView.getWidth()/2,mapView.getHeight()/2);
            //创建地图状态
            MapStatus status=new MapStatus.Builder()
                    .rotate(rotate)
                    .overlook(overlook)
                    .target(latlng)
                    .targetScreen(point)
                    .zoom(zoom).build();
            //创建地图更新状态
            MapStatusUpdate updateupdate=MapStatusUpdateFactory.newMapStatus(status);


//            MyLocationData data=new MyLocationData.Builder()
//                    .accuracy(location.getRadius())
//                    .direction(0)
//                    .latitude(location.getLatitude())
//                    .longitude(location.getLongitude())
//                    .build();
//            baiduMap.setMyLocationData(data);
            currentMarker= BitmapDescriptorFactory.fromResource(R.drawable.icon_openmap_mark);
            OverlayOptions option = new MarkerOptions()
                    .position(latlng)
                    .icon(currentMarker);
            baiduMap.clear();
            baiduMap.addOverlay(option);
//            MyLocationConfiguration myLocationConfiguration=new MyLocationConfiguration(mCurrentMode,true,currentMarker);
//            baiduMap.setMyLocationConfiguration(myLocationConfiguration);
            //更新地图信息
            baiduMap.setMapStatus(updateupdate);
            baiduMap.setMyLocationEnabled(false);
        }else {
        //没有权限则申请权限
            requestGPSPermission();
        }

    }

    //开启轨迹采集服务
    private void startTraceService(){
        mTraceClient.startTrace(mTrace,traceListener);
        mTraceClient.startGather(traceListener);
    }
    //关闭轨迹采集服务
    private void stopTraceService(){
        mTraceClient.stopTrace(mTrace,traceListener);
        mTraceClient.stopGather(traceListener);
    }

    //位置监听回调
    public class MyLocationListener implements BDLocationListener
    {

        @Override
        public void onConnectHotSpotMessage(String s, int i) {

        }

        @Override
        public void onReceiveLocation(BDLocation location) {

            //获取定位结果
            StringBuffer sb = new StringBuffer(256);

            sb.append("time : ");
            sb.append(location.getTime());    //获取定位时间

            sb.append("\nerror code : ");
            sb.append(location.getLocType());    //获取类型类型

            sb.append("\nlatitude : ");
            sb.append(location.getLatitude());    //获取纬度信息

            sb.append("\nlontitude : ");
            sb.append(location.getLongitude());    //获取经度信息

            sb.append("\nradius : ");
            sb.append(location.getRadius());    //获取定位精准度

            if (location.getLocType() == BDLocation.TypeGpsLocation){

                // GPS定位结果
                sb.append("\nspeed : ");
                sb.append(location.getSpeed());    // 单位：公里每小时

                sb.append("\nsatellite : ");
                sb.append(location.getSatelliteNumber());    //获取卫星数

                sb.append("\nheight : ");
                sb.append(location.getAltitude());    //获取海拔高度信息，单位米

                sb.append("\ndirection : ");
                sb.append(location.getDirection());    //获取方向信息，单位度

                sb.append("\naddr : ");
                sb.append(location.getAddrStr());    //获取地址信息

                sb.append("\ndescribe : ");
                sb.append("gps定位成功");

            } else if (location.getLocType() == BDLocation.TypeNetWorkLocation){

                // 网络定位结果
                sb.append("\naddr : ");
                sb.append(location.getAddrStr());    //获取地址信息

                sb.append("\noperationers : ");
                sb.append(location.getOperators());    //获取运营商信息

                sb.append("\ndescribe : ");
                sb.append("网络定位成功");

            } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {

                // 离线定位结果
                sb.append("\ndescribe : ");
                sb.append("离线定位成功，离线定位结果也是有效的");

            } else if (location.getLocType() == BDLocation.TypeServerError) {

                sb.append("\ndescribe : ");
                sb.append("服务端网络定位失败，可以反馈IMEI号和大体定位时间到loc-bugs@baidu.com，会有人追查原因");

            } else if (location.getLocType() == BDLocation.TypeNetWorkException) {

                sb.append("\ndescribe : ");
                sb.append("网络不同导致定位失败，请检查网络是否通畅");

            } else if (location.getLocType() == BDLocation.TypeCriteriaException) {

                sb.append("\ndescribe : ");
                sb.append("无法获取有效定位依据导致定位失败，一般是由于手机的原因，处于飞行模式下一般会造成这种结果，可以试着重启手机");

            }

            sb.append("\nlocationdescribe : ");
            sb.append(location.getLocationDescribe());    //位置语义化信息

            List<Poi> list = location.getPoiList();    // POI数据
            if (list != null) {
                sb.append("\npoilist size = : ");
                sb.append(list.size());
                for (Poi p : list) {
                    sb.append("\npoi= : ");
                    sb.append(p.getId() + " " + p.getName() + " " + p.getRank());
                }
            }

            int errorType=location.getLocType();
            sb.append("\nLocType=:");
            sb.append(errorType);

            Log.i("BaiduLocationApiDem", sb.toString());
        }
    }

    //轨迹服务器初始化坚挺
    OnTraceListener traceListener=new OnTraceListener() {
        @Override
        public void onStartTraceCallback(int i, String s) {
            toast.setText("轨迹服务开启");
            toast.show();
        }

        @Override
        public void onStopTraceCallback(int i, String s) {
            toast.setText("轨迹服务停止");
            toast.show();
        }

        @Override
        public void onStartGatherCallback(int i, String s) {
            toast.setText("轨迹信息采集开始");
            toast.show();
        }

        @Override
        public void onStopGatherCallback(int i, String s) {
            toast.setText("轨迹信息采集停止");
            toast.show();
        }

        @Override
        public void onPushCallback(byte b, PushMessage pushMessage) {

        }
    };

    //locationClient获取位置的信息设置
    private void initLocation(){
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备

        option.setCoorType("bd09ll");
        //可选，默认gcj02，设置返回的定位结果坐标系

        int span=1000;
        option.setScanSpan(span);
        //可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的

        option.setIsNeedAddress(true);
        //可选，设置是否需要地址信息，默认不需要

        option.setOpenGps(true);
        //可选，默认false,设置是否使用gps

        option.setLocationNotify(true);
        //可选，默认false，设置是否当GPS有效时按照1S/1次频率输出GPS结果

        option.setIsNeedLocationDescribe(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”

        option.setIsNeedLocationPoiList(true);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到

        option.setIgnoreKillProcess(false);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死

        option.SetIgnoreCacheException(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集

        option.setEnableSimulateGps(false);
        //可选，默认false，设置是否需要过滤GPS仿真结果，默认需要

        mLocationClient.setLocOption(option);
    }


    //检测网络是否可用
    private boolean isNetworkConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = cm.getActiveNetworkInfo();
        if (network != null) {
            return network.isAvailable();
        }
        return false;
    }

}
