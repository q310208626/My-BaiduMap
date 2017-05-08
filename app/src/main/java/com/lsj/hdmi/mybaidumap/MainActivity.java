package com.lsj.hdmi.mybaidumap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
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
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.Trace;
import com.baidu.trace.api.track.ClearCacheTrackResponse;
import com.baidu.trace.api.track.DistanceResponse;
import com.baidu.trace.api.track.HistoryTrackRequest;
import com.baidu.trace.api.track.HistoryTrackResponse;
import com.baidu.trace.api.track.LatestPointResponse;
import com.baidu.trace.api.track.OnTrackListener;
import com.baidu.trace.api.track.QueryCacheTrackResponse;
import com.baidu.trace.api.track.TrackPoint;
import com.baidu.trace.model.OnTraceListener;
import com.baidu.trace.model.PushMessage;


import java.util.ArrayList;
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
    int tag=1;      //请求标识
    private long traceServiceId=140176;
    private String entityName;
    boolean isNeedObjectStorage = false;
    private Trace mTrace;
    private LBSTraceClient mTraceClient;
    private OnTraceListener myTraceListener =new MyOnTraceListener();
    HistoryTrackRequest historyTrackRequest;
    private List<LatLng> historyLatlngs=new ArrayList<LatLng>();    //记录历史点
    public boolean isCallDrawTrace=false;


    Toast toast;
    String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION};    //gps权限

    private TextView locationTextview;
    private StringBuffer locationStringBuffer =new StringBuffer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        init();
        startGetLocation(); //启动获取定位
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
                        startGetLocation();
                        requestGPSPermission();
                    }
                break;
            case R.id.menuitem_mylocation:
                    getMyLocation();
                break;
            case R.id.menuitem_traceshow:

//                if (mTraceClient!=null){
//                    startTraceService();
//
//                }else {
//                    Log.d(TAG, "onOptionsItemSelected: --------------mTraceClient==null");
//                }
                if (!isCallDrawTrace){
                    isCallDrawTrace=true;
                    new Thread(callDrawTraceRunnable).start();
                    toast.setText("开始记录轨迹");
                    toast.show();
                }else {
                    Log.d(TAG, "onOptionsItemSelected: ---------------threadisAlive");
                    isCallDrawTrace=false;
                }



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
        stopTraceService();
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
        locationTextview= (TextView) findViewById(R.id.location_etxtview);
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
        entityName=getIMEI();
        Log.d(TAG, "init: -------------------------entityName"+entityName);
        mTrace = new Trace(traceServiceId, entityName, isNeedObjectStorage);
        mTraceClient= new LBSTraceClient(getApplicationContext());//初始化轨迹服务端
        int gatherInterval = 3;                                     // 定位周期(单位:秒)
        int packInterval = 3;                                      // 打包回传周期(单位:秒)
        mTraceClient.setInterval(gatherInterval, packInterval);     // 设置定位和打包周期
        mTraceClient.setOnTraceListener(myTraceListener);


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
        Log.d(TAG, "checkPermission: -----------------permission"+hasPermission);
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
            Log.d(TAG, "getMyLocation: -------------------location"+location);
            int error=location.getLocType();
            Log.d(TAG, "getMyLocation: -------------------errorType"+error);
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
        mTraceClient.startTrace(mTrace,null);

    }
    //关闭轨迹采集服务
    private void stopTraceService(){
        if (mTraceClient!=null){
            mTraceClient.stopTrace(mTrace, null);
            mTraceClient.stopGather(null);
        }
    }

    //画轨迹方法
    private void showTrace(){


//        List<String> entityNames=new ArrayList<String>();
//        entityNames.add(entityName);
//        ClearCacheTrackRequest clearCacheTrackRequest=new ClearCacheTrackRequest(tag,traceServiceId,entityNames);
//        mTraceClient.clearCacheTrack(clearCacheTrackRequest,hostoryTraceListener);
        historyTrackRequest = new HistoryTrackRequest(tag, traceServiceId, entityName);
        //设置轨迹查询起止时间
        long startTime = System.currentTimeMillis() / 1000 - 12 * 60 * 60;  // 开始时间(单位：秒)
        long endTime = System.currentTimeMillis() / 1000;                   // 结束时间(单位：秒)
        historyTrackRequest.setStartTime(startTime);                        // 设置开始时间
        historyTrackRequest.setEndTime(endTime);                            // 设置结束时间

//        // 创建纠偏选项实例
//        ProcessOption processOption = new ProcessOption();
//        // 设置需要去噪
//        processOption.setNeedDenoise(true);
//        // 设置需要抽稀
//        processOption.setNeedVacuate(true);
//        // 设置需要绑路
//        processOption.setNeedMapMatch(true);
//        // 设置精度过滤值(定位精度大于100米的过滤掉)
//        processOption.setRadiusThreshold(30);
//        processOption.setTransportMode(TransportMode.walking);
//        historyTrackRequest.setProcessOption(processOption);
//        historyTrackRequest.setSupplementMode(SupplementMode.walking);

        mTraceClient.queryHistoryTrack(historyTrackRequest,hostoryTraceListener);

    }

    //位置监听回调
    public class MyLocationListener implements BDLocationListener
    {

        @Override
        public void onConnectHotSpotMessage(String s, int i) {

        }

        @Override
        public void onReceiveLocation(BDLocation location) {

            if (location!=null){
                LatLng latLng=new LatLng(location.getLatitude(),location.getLongitude());
                historyLatlngs.add(latLng);
            }

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

                ;

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

            if (location!=null){
                locationStringBuffer.append("大概位置: "+location.getLocationDescribe()+"\n");
                locationStringBuffer.append("经度: "+location.getLatitude()+"\n");
                locationStringBuffer.append("维度: "+location.getLongitude()+"\n");
            }
            Message message=new Message();
            message.what=2;
            handler.sendMessage(message);

        }
    }

    //轨迹服务器初始化监听
   public class MyOnTraceListener implements OnTraceListener{
        @Override
        public void onStartTraceCallback(int i, String s) {
            toast.setText("轨迹服务开启");
            toast.show();
            mTraceClient.startGather(myTraceListener);
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
            showTrace();
        }

        @Override
        public void onStopGatherCallback(int i, String s) {
            toast.setText("轨迹信息采集停止");
            toast.show();
        }

        @Override
        public void onPushCallback(byte b, PushMessage pushMessage) {

        }

    }

    //历史轨迹监听器
    OnTrackListener hostoryTraceListener=new OnTrackListener() {
        @Override
        public void onHistoryTrackCallback(HistoryTrackResponse historyTrackResponse) {

            com.baidu.trace.model.Point startPoint=historyTrackResponse.getStartPoint();
            com.baidu.trace.model.Point endPoint=historyTrackResponse.getEndPoint();
            Log.d(TAG, "onHistoryTrackCallback: -----------------------startPoint"+startPoint.getLocation().getLatitude()+" "+startPoint.getLocation().getLongitude());
            Log.d(TAG, "onHistoryTrackCallback: -----------------------endPoint"+endPoint.getLocation().getLatitude()+" "+endPoint.getLocation().getLongitude());
            locationTextview.setText("");
            locationTextview.append(startPoint.getLocation().getLatitude()+"\n");
            locationTextview.append(startPoint.getLocation().getLongitude()+"\n");
            locationTextview.append(endPoint.getLocation().getLatitude()+"\n");
            locationTextview.append(endPoint.getLocation().getLongitude()+"");
            List<TrackPoint> points=historyTrackResponse.getTrackPoints();

            List<LatLng> latLngs=pointToLaTLng(points);
//            latLngs.clear();
//            for(int i=0;i<10;i++){
//                double lat=startPoint.getLocation().getLatitude()+0.00002*i;
//                double lon=startPoint.getLocation().getLongitude()+0.00001*i;
//                latLngs.add(new LatLng(lat,lon));
//            }
            latLngs=getRealPoint(latLngs);
            drawHistoryTrack(latLngs);
           // super.onHistoryTrackCallback(historyTrackResponse);
        }

        @Override
        public void onDistanceCallback(DistanceResponse distanceResponse) {
            super.onDistanceCallback(distanceResponse);
        }

        @Override
        public void onLatestPointCallback(LatestPointResponse latestPointResponse) {
            super.onLatestPointCallback(latestPointResponse);
        }

        @Override
        public void onQueryCacheTrackCallback(QueryCacheTrackResponse queryCacheTrackResponse) {
            super.onQueryCacheTrackCallback(queryCacheTrackResponse);
        }

        @Override
        public void onClearCacheTrackCallback(ClearCacheTrackResponse clearCacheTrackResponse) {
            Log.d(TAG, "onClearCacheTrackCallback: ---------------------清除轨迹");
            super.onClearCacheTrackCallback(clearCacheTrackResponse);
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

    private List<LatLng> pointToLaTLng(List<TrackPoint> points){
        List<LatLng> latlngs=new ArrayList<LatLng>();
        for (TrackPoint trackPoint:points){
            latlngs.add(new LatLng(trackPoint.getLocation().getLatitude(),trackPoint.getLocation().getLongitude()));
        }
        return latlngs;
    }

    public void drawHistoryTrack(final List<LatLng> points) {
        // 绘制新覆盖物前，清空之前的覆盖物
        baiduMap.clear();

        if (points == null || points.size() == 0) {
            Toast.makeText(this, "当前查询无轨迹点", Toast.LENGTH_SHORT).show();
        } else if (points.size() > 1) {

            LatLng llC = points.get(0);
            LatLng llD = points.get(points.size() - 1);
            LatLngBounds bounds = new LatLngBounds.Builder()
                    .include(llC).include(llD).build();

            MapStatusUpdate msUpdate = MapStatusUpdateFactory.newLatLngBounds(bounds);

            BitmapDescriptor bmStart = BitmapDescriptorFactory.fromResource(R.drawable.icon_openmap_focuse_mark);
            BitmapDescriptor bmEnd = BitmapDescriptorFactory.fromResource(R.drawable.icon_openmap_mark);

            // 添加起点图标
            MarkerOptions startMarker = new MarkerOptions()
                    .position(points.get(points.size() - 1)).icon(bmStart)
                    .zIndex(9).draggable(true);
            Log.d(TAG, "drawHistoryTrack: -----------------------起点"+points.get(points.size() - 1).latitude+" "+points.get(points.size() - 1).longitude);

            // 添加终点图标
            MarkerOptions endMarker = new MarkerOptions().position(points.get(0))
                    .icon(bmEnd).zIndex(9).draggable(true);
            Log.d(TAG, "drawHistoryTrack: -----------------------终点"+points.get(0).latitude+" "+points.get(0).longitude);
            // 添加路线（轨迹）
            PolylineOptions polyline = new PolylineOptions().width(10)
                    .color(Color.RED).points(points);

            baiduMap.setMapStatus(msUpdate);
            baiduMap.addOverlay(startMarker);
            baiduMap.addOverlay(endMarker);
            baiduMap.addOverlay(polyline);

        }

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

    //获取设备标识
    public String getIMEI() {
        TelephonyManager TelephonyMgr = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        String szImei = TelephonyMgr.getDeviceId();
        return szImei;
    }

    //去除轨迹中非法点
    private List<LatLng> getRealPoint(List<LatLng> latLngs){
        List<LatLng> noZeroPoints=new ArrayList<LatLng>();
        List<LatLng> realPoints=new ArrayList<LatLng>();

        //去除0.0
        for (int i=0;i<latLngs.size();i++){
            LatLng latLng=latLngs.get(i);
            if (Math.abs(latLng.latitude-0.0)<0.01&&Math.abs(latLng.longitude-0.0)<0.01){
            }else {
                noZeroPoints.add(latLng);
            }
        }
        for (int i=0;i<noZeroPoints.size()-2;){
            LatLng latLng=noZeroPoints.get(i);
            LatLng latLng1=noZeroPoints.get(i+1);
            if (Math.abs(latLng.latitude-latLng1.latitude)>0.0005){
                noZeroPoints.remove(i+1);
                break;
            }else  if (Math.abs(latLng.longitude-latLng1.longitude)>0.0005){
                noZeroPoints.remove(i+1);
                break;
            }else {
                realPoints.add(latLng);
                i++;
            }
        }
        realPoints=noZeroPoints;
        return realPoints;
    }

    Runnable callDrawTraceRunnable = new Runnable() {
        @Override
        public void run() {
            while(isCallDrawTrace){
                historyLatlngs=getRealPoint(historyLatlngs);
                drawHistoryTrack(historyLatlngs);
                Log.d(TAG, "run: -------------------------------callDrawRunning");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //线程发送信息到主线程,提醒ui更新
            Message message=new Message();
            message.what=1;
            handler.sendMessage(message);
        }


    };

    Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch  (msg.what){
                case 1:
                    toast.setText("停止记录轨迹");
                    toast.show();
                    break;
                case 2:
                    locationTextview.setText(locationStringBuffer);
                    locationStringBuffer.delete(0,locationStringBuffer.length());
                    break;
            }
        }
    };

}
