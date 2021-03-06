package com.horacework.controller;


import com.horacework.model.*;
import com.horacework.repository.*;
import com.horacework.utils.JsonUtil;
import com.horacework.utils.MyPrivateKey;
import com.horacework.utils.RSAUtils;
import com.horacework.utils.SuccessStateObj;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/client")
public class ClientController extends BaseController {

    @Autowired
    private UserRepository mUserRepo;
    @Autowired
    private UserlogRepository mUserlogRepo;
    @Autowired
    private UsermoneyRepository mUsermoneyRepository;
    @Autowired
    private UsercarRepository mUsercarRepository;
    @Autowired
    private UserParkingRepository mUserParkingRepository;
    @Autowired
    private UserFavoriteRepository mUserFavoriteRepository;
    @Autowired
    private UserOrderRepository mUserOrderRepository;
    @Autowired
    private UserFeedbackRepository mUserFeedbackRepository;
    @Autowired
    private ParkingStatusRepository mParkingStatusRepository;


    @Autowired
    private MarkeridRepository markeridRepo;
    @Autowired
    private MarkerinfoRepository markerinfoRepo;

    private String privateKey = MyPrivateKey.getPrivateKey();

    //用户数据操作

    //用户的注册与登入登出API
    @RequestMapping(value = "/getUser",method = RequestMethod.GET)//仅供测试
    public void getUser(){
        List<UserEntity> results=mUserRepo.findAll();
        String restStr;

        restStr=results==null?"{}": JsonUtil.toJson(results);

        try {
            response.getWriter().write(restStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @RequestMapping(value = "/userSignup",method = RequestMethod.POST)
    public void userSignup(@RequestParam String username , @RequestParam String password , @RequestParam String password2 , @RequestParam String deviceid) throws Exception {
        String resultStr;

        if (!password.equals(password2)){
            //两次密码不一致的情况
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"两次密码不一致"));
        }else if(username.equals("") || password.equals("") || deviceid.equals("") ){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"参数不能为空"));
        }else if (mUserRepo.userExist(username) != null){
            //查询username是否被占用
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"该username已被占用"));
        }else {
            UserEntity newUser = new UserEntity();
            newUser.setId(UUID.randomUUID().toString());
            newUser.setName(username);
            newUser.setPassword(password);
            UserEntity userEntity = mUserRepo.saveAndFlush(newUser);
            userEntity.setPassword("****");
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"注册成功",userEntity));
            //注册成功后自动给账户充值10块钱
            UsermoneyEntity usermoneyChange = new UsermoneyEntity();
            usermoneyChange.setUserId(userEntity.getId());
            usermoneyChange.setCurrentTime(new Timestamp(System.currentTimeMillis()));
            usermoneyChange.setType(2);
            usermoneyChange.setFigure(10);
            usermoneyChange.setRemain(10);
            UsermoneyEntity usermoneyResult = mUsermoneyRepository.saveAndFlush(usermoneyChange);
            //注册成功后自动登录，记录插入
            UserlogEntity userlog = new UserlogEntity();
            userlog.setUserId(userEntity.getId());
            userlog.setDeviceId(deviceid);
            userlog.setIsLoginOut(0);
            userlog.setLoginTime(new Timestamp(System.currentTimeMillis()));
            UserlogEntity logResult = mUserlogRepo.saveAndFlush(userlog);
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/usernameCheck",method = RequestMethod.GET)//分离出来待使用，注册功能已包括
    public void usernameChack(@RequestParam String data) throws Exception {
        String resultStr;
        String username = RSAUtils.DecodeDataToString(data,privateKey);
        //String username = "che";//测试数据
        UserEntity usernameExist = mUserRepo.userExist(username);
        if (usernameExist != null){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"该username已被占用"));
        }else {
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"该username可以被注册"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userLogin",method = RequestMethod.POST)
    public void userLogin(@RequestParam String username ,@RequestParam String password , @RequestParam String deviceid) throws Exception {
        String resultStr;

        if (username.equals("") || password.equals("") || deviceid.equals("") ){
            //username与password不能为空
            resultStr=JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"参数不能为空"));
        }else {
            UserEntity result = mUserRepo.findUserSerect(username);
            try {
                if (password.equals(result.getPassword())){
                    result.setPassword("****");//隐藏密码数据返回JSON
                    resultStr=JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"登录成功",result));
                    //插入userlog日志数据表
                    UserlogEntity userlog = new UserlogEntity();
                    userlog.setUserId(result.getId());
                    userlog.setDeviceId(deviceid);
                    userlog.setIsLoginOut(0);
//                    Date now = new Date();
//                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    userlog.setLoginTime(new Timestamp(System.currentTimeMillis()));
                    UserlogEntity logResult = mUserlogRepo.saveAndFlush(userlog);

                }else {
                    resultStr=JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"用户名或密码错误"));
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                resultStr=JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"查无此用户"));
            }
        }
        response.getWriter().write(resultStr);//返回已组装好的JSON
    }
    @RequestMapping(value = "/userLogout",method = RequestMethod.POST)
    public void userLogout(@RequestParam String userid , @RequestParam String deviceid) throws Exception{
        String resultStr;
        try {
            UserlogEntity userlogEntity = mUserlogRepo.findUserLogout(userid ,deviceid);
            userlogEntity.setLogoutTime(new Timestamp(System.currentTimeMillis()));
            userlogEntity.setIsLoginOut(1);
            UserlogEntity userLogoutResult = mUserlogRepo.saveAndFlush(userlogEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"登出成功"));
        } catch (NullPointerException e) {
            e.printStackTrace();
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"数据发生错误"));
        }
        response.getWriter().write(resultStr);
    }

    //用户的服务API

    //用户余额操作
    @RequestMapping(value = "/userCheckMyMoney",method = RequestMethod.GET)
    public void userCheckMyMoney(@RequestParam String userid) throws Exception {
        //获取用户余额
        String resultStr;
        try {
            UsermoneyEntity usermoneyEntity = mUsermoneyRepository.findUserMoneyLastLogById(userid);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询成功",usermoneyEntity));
        } catch (NullPointerException e) {
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"查询失败"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userMyMoneyLog",method = RequestMethod.GET)
    public void userMyMoneyLog(@RequestParam String userid) throws Exception {
        //获取用户余额的支出记录
        String resultStr;
        try {
            List<UsermoneyEntity> usermoneyEntityList = mUsermoneyRepository.findUserMoneyAllLogById(userid);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询成功",usermoneyEntityList));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"查询记录失败"));
        }
        response.getWriter().write(resultStr);
    }
    public String changeUserMoney(String userid , int type , int figure) throws Exception {
        //改变用户余额
        //Type变量约定：1为支出，2为充值
        String resultStr;
        int moneyRemain;
        try {
            UsermoneyEntity usermoneyEntity = mUsermoneyRepository.findUserMoneyLastLogById(userid);
            UsermoneyEntity usermoneyChange = new UsermoneyEntity();
            UsermoneyEntity usermoneyResult;
            moneyRemain = usermoneyEntity.getRemain();
            switch (type){
                case 1:
                    if (moneyRemain < figure){
                        //余额不足
                        resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"余额不足"));
                    }else {
                        moneyRemain = moneyRemain - figure;
                        usermoneyChange.setUserId(userid);
                        usermoneyChange.setCurrentTime(new Timestamp(System.currentTimeMillis()));
                        usermoneyChange.setType(type);
                        usermoneyChange.setFigure(figure);
                        usermoneyChange.setRemain(moneyRemain);
                        usermoneyResult = mUsermoneyRepository.saveAndFlush(usermoneyChange);
                        resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"交易成功",usermoneyResult));
                    }
                    break;
                case 2:
                    moneyRemain = moneyRemain + figure;
                    usermoneyChange.setUserId(userid);
                    usermoneyChange.setCurrentTime(new Timestamp(System.currentTimeMillis()));
                    usermoneyChange.setType(type);
                    usermoneyChange.setFigure(figure);
                    usermoneyChange.setRemain(moneyRemain);
                    usermoneyResult = mUsermoneyRepository.saveAndFlush(usermoneyChange);
                    resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"充值成功",usermoneyResult));
                    break;
                default:
                    resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"类型码错误"));
            }
        } catch (NullPointerException e) {
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"查询失败"));
        }
        return resultStr;
    }

    //用户停车状态
    @RequestMapping(value = "/userParkingStatus",method = RequestMethod.GET)
    public void userParkingStatus(@RequestParam String userid) throws IOException{
        String resultStr;
        List<ParkinglogEntity> parkingList = mUserParkingRepository.userParkingStatusById(userid);
        resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询成功",parkingList.size()));
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userParkingStatusInfo",method = RequestMethod.GET)
    public void userParkingStatusInfo(@RequestParam String userid) throws IOException{
        String resultStr;
        try {
            List<ParkinglogEntity> parkingList = mUserParkingRepository.userParkingStatusById(userid);
            List<UserParkingStatusEntity> showList = new ArrayList<UserParkingStatusEntity>();
            for (ParkinglogEntity item : parkingList){
                UserParkingStatusEntity statusEntity = new UserParkingStatusEntity();
                statusEntity.setLogId(item.getLogId());
                statusEntity.setEnterTime(item.getEnterTime());
                MarkerinfoEntity markerinfoEntity = markerinfoRepo.findOne(item.getMarkerId());
                statusEntity.setMarkerName(markerinfoEntity.getName());
                statusEntity.setPrice(markerinfoEntity.getPrice());
                UsercarEntity usercarEntity = mUsercarRepository.findUserCarNameById(item.getCarId());
                statusEntity.setCarName(usercarEntity.getPlate());
                showList.add(statusEntity);
            }
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询成功",showList));
        } catch (NullPointerException e) {
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"查询失败"));
        }
        response.getWriter().write(resultStr);
    }

    //用户车牌操作
    @RequestMapping(value = "/userCars",method = RequestMethod.GET)
    public void findUserCarsById(@RequestParam String userid) throws Exception{
        String resultStr;
        try {
            List<UsercarEntity> usercarEntityList = mUsercarRepository.findUserCarById(userid);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询用户车牌成功",usercarEntityList));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"该用户无车牌"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userAddCars",method = RequestMethod.POST)
    public void userAddCarsById(@RequestParam String userid , @RequestParam String plate) throws Exception{
        String resultStr;
        try {
            UsercarEntity usercarEntity = new UsercarEntity();
            usercarEntity.setCarId(UUID.randomUUID().toString());
            usercarEntity.setUserId(userid);
            usercarEntity.setPlate(plate);
            mUsercarRepository.saveAndFlush(usercarEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"用户车牌添加成功"));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"用户车牌添加失败"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userDeleteCar" ,method = RequestMethod.POST)
    public void  userDeleteCar(@RequestParam String userid , @RequestParam String carid) throws Exception {
        String resultStr;
        try {
            UsercarEntity usercarEntity = mUsercarRepository.findOneUserCarById(userid,carid);
            usercarEntity.setIsDel(1);
            mUsercarRepository.saveAndFlush(usercarEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"车牌删除成功"));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"车牌删除失败"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userCarName",method = RequestMethod.GET)
    public void userCarName(@RequestParam String carid) throws IOException {
        String resultStr;
        try {
            UsercarEntity usercarEntity = mUsercarRepository.findUserCarNameById(carid);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"车牌名称查询成功",usercarEntity));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"车牌名称查询失败"));
        }
        response.getWriter().write(resultStr);
    }

    //用户停车记录
    @RequestMapping(value = "/userMyParkingLog" ,method = RequestMethod.GET)
    public void userMyParkingLog(@RequestParam String userid) throws IOException{
        String resultStr;
        try {
            List<ParkinglogShowEntity> resList = new ArrayList<ParkinglogShowEntity>();
            List<ParkinglogEntity> parkinglogEntityList = mUserParkingRepository.findUserParkingAllLogById(userid);
            for(ParkinglogEntity i : parkinglogEntityList){
                ParkinglogShowEntity p = new ParkinglogShowEntity();
                p.setLogId(i.getLogId());
                p.setEnterTime(i.getEnterTime());
                p.setLeaveTime(i.getLeaveTime());
                UsercarEntity usercarEntity = mUsercarRepository.findUserCarNameById(i.getCarId());
                p.setPlate(usercarEntity.getPlate());
                MarkerinfoEntity markerinfoEntity = markerinfoRepo.findOne(i.getMarkerId());
                p.setName(markerinfoEntity.getName());
                resList.add(p);
            }
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"停车记录",resList));
        } catch (NullPointerException e) {
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"没有停车记录"));
        }
        response.getWriter().write(resultStr);
    }
//    @RequestMapping(value = "/userMyParkingLogg" ,method = RequestMethod.GET)
//    public void userMyParkingLogg(@RequestParam String userid) throws Exception{
//        String resultStr;
//        EntityManagerFactory factory = Persistence.createEntityManagerFactory("org.hibernate.ejb.HibernatePersistence");
//        EntityManager em = factory.createEntityManager();
//        List<ParkinglogShowEntity> p = em.createQuery("select new com.horacework.model.ParkinglogShowEntity(ParkinglogEntity.logId,ParkinglogEntity.enterTime,ParkinglogEntity .leaveTime,UsercarEntity .plate,MarkerinfoEntity .name) " +
//                "from ParkinglogEntity a ,UsercarEntity b , MarkerinfoEntity c " +
//                "where a.carId=b.carId and a.markerId=c.id and a.userId="+userid)
//                .getResultList();
//        em.close();
//        factory.close();
//        resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"停车记录",p));
//        response.getWriter().write(resultStr);
//    }

    //用户收藏
    @RequestMapping(value = "/userFavoriteList",method = RequestMethod.GET)
    public void userFavoritePark(@RequestParam String userid)throws IOException{
        String resStr;
        try {
            List<UserfavoriteShowEntity> resultList = new ArrayList<UserfavoriteShowEntity>();
            List<UserfavoriteEntity> userfavoriteEntityList = mUserFavoriteRepository.findUserFavoriteByUserId(userid);
            for (UserfavoriteEntity item : userfavoriteEntityList){
                UserfavoriteShowEntity showEntity = new UserfavoriteShowEntity();
                MarkerinfoEntity markerinfoEntity = markerinfoRepo.findOne(item.getMarkerId());
                showEntity.setId(item.getId());
                showEntity.setMarkerId(item.getMarkerId());
                showEntity.setName(markerinfoEntity.getName());
                showEntity.setPrice(markerinfoEntity.getPrice());
                resultList.add(showEntity);
            }
            resStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查找收藏记录成功",resultList));
        }catch (NullPointerException e){
            resStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"无收藏记录"));
        }
        response.getWriter().write(resStr);
    }
    @RequestMapping(value = "/userFavoriteDelete",method = RequestMethod.POST)
    public void  userFavorietDelete(@RequestParam String id ,@RequestParam String userid) throws IOException {
        String resultStr;
        try {
            UserfavoriteEntity userfavoriteEntity = mUserFavoriteRepository.UserFavoriteDelete(id,userid);
            userfavoriteEntity.setCancelTime(new Timestamp(System.currentTimeMillis()));
            userfavoriteEntity.setIsCancel(1);
            UserfavoriteEntity resEntity = mUserFavoriteRepository.saveAndFlush(userfavoriteEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"删除收藏成功"));
        } catch (NullPointerException e) {
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"删除收藏失败"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userFavoriteCheck",method = RequestMethod.GET)
    public void  userFavoriteCheck(@RequestParam String userid , @RequestParam String markerid) throws IOException {
        String resultStr;
        List<UserfavoriteEntity> userfavoriteList = mUserFavoriteRepository.UserFavoriteCheck(userid,markerid);
        if (userfavoriteList.size()>0){
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"用户已收藏"));
        }else {
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"用户未收藏"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userFavoriteAdd",method = RequestMethod.POST)
    public void  userFavoriteAdd(@RequestParam String userid , @RequestParam String markerid) throws IOException {
        String resultStr;
        UserfavoriteEntity entity = new UserfavoriteEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userid);
        entity.setMarkerId(markerid);
        entity.setCurrentTime(new Timestamp(System.currentTimeMillis()));
        mUserFavoriteRepository.saveAndFlush(entity);
        resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"收藏成功"));
        response.getWriter().write(resultStr);
    }

    //用户预订相关API
    @RequestMapping(value = "/userOrderShow",method = RequestMethod.GET)
    public void  userOrderShow(@RequestParam String userid) throws IOException {
        String resultStr;
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        try {
            List<UserorderShowEntity> orderShowList = new ArrayList<UserorderShowEntity>();
            List<UserorderEntity> orderList = mUserOrderRepository.findUserOrderByUserId(userid,currentTime);
            for (UserorderEntity item : orderList){
                UserorderShowEntity showItem = new UserorderShowEntity();
                showItem.setOrderId(item.getOrderId());
                showItem.setMarkerId(item.getMarkerId());
                showItem.setOrderTime(item.getOrderTime());
                MarkerinfoEntity markerinfoEntity = markerinfoRepo.findOne(item.getMarkerId());
                showItem.setMarkName(markerinfoEntity.getName());
                orderShowList.add(showItem);
            }
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"查询订单成功",orderShowList));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"没有预订订单"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userOrderDelete",method = RequestMethod.POST)
    public void  userOrderDelete(@RequestParam String orderid ,@RequestParam String userid) throws IOException {
        String resultStr;
        try {
            UserorderEntity userorderEntity = mUserOrderRepository.findUserOrderByOrderIdAndUserId(orderid,userid);
            userorderEntity.setIsDel(1);
            mUserOrderRepository.saveAndFlush(userorderEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"删除订单成功"));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"删除订单失败"));
        }
        response.getWriter().write(resultStr);
    }
    @RequestMapping(value = "/userOrderBook",method = RequestMethod.POST)
    public void userOrderBook(@RequestParam String userid,@RequestParam String markerid,@RequestParam long ordertime) throws IOException {
        String resultStr;
        //先查重
        Timestamp ordertimeTime  = new Timestamp(ordertime);
        List<UserorderEntity> isRepeat = mUserOrderRepository.checkUserOrderIsRepeat(userid,ordertimeTime,markerid);
        if (isRepeat.size()>0){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"不能提交订单重复的订单"));
        }else {
            UserorderEntity newOrder = new UserorderEntity();
            newOrder.setOrderId(UUID.randomUUID().toString());
            newOrder.setUserId(userid);
            newOrder.setMarkerId(markerid);
            newOrder.setOrderTime(ordertimeTime);
            newOrder.setPresent(new Timestamp(System.currentTimeMillis()));
            mUserOrderRepository.saveAndFlush(newOrder);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"提交订单成功"));
        }
        response.getWriter().write(resultStr);
    }

    //用户反馈
    @RequestMapping(value = "/userFeedback",method = RequestMethod.POST)
    public void getUserFeedback(@RequestParam String userid , @RequestParam String tel , @RequestParam String content) throws Exception{
        String resultStr;
        FeedbackEntity feedbackEntity = new FeedbackEntity();

        feedbackEntity.setUserId(userid);
        feedbackEntity.setTelphone(tel);
        feedbackEntity.setContent(content);
        feedbackEntity.setCurrentTime(new Timestamp(System.currentTimeMillis()));
        try {
            mUserFeedbackRepository.saveAndFlush(feedbackEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"意见已提交，我们会尽快处理"));
        }catch (NullPointerException e){
            resultStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"提交失败"));
        }
        response.getWriter().write(resultStr);
    }


    //地图中Marker数据
    @RequestMapping(value = "/getAllMarkerId",method = RequestMethod.GET)
    public void getAllMarkerId() throws Exception {
        //List<UserEntity> results=mUserRepo.findAll();
        List<MarkeridEntity> results = markeridRepo.findAll();
        String resStr;
        if (results == null) {
            resStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"数据发生错误"));
        } else {
            resStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"数据查询成功",results));
        }
        try {
            response.getWriter().write(resStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @RequestMapping(value = "/getAllMarkerInfo",method = RequestMethod.GET)
    public void getAllMarkerInfo() throws Exception {
        //List<UserEntity> results=mUserRepo.findAll();
        List<MarkerinfoEntity> results = markerinfoRepo.findAll();
        String resStr;
        if (results == null) {
            resStr = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"数据发生错误"));
        } else {
            resStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"数据查询成功",results));
        }
        try {
            response.getWriter().write(resStr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @RequestMapping(value = "/getMarkerInfoById",method = RequestMethod.GET)
    public void getMarkerInfoById (@RequestParam String id) throws Exception {

        MarkerinfoEntity result = markerinfoRepo.findOne(id);
        String string;
        if (result == null) {
            string = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"数据发生错误","{}"));
        } else {
            string = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"数据查找成功",result));
        }
        try {
            response.getWriter().write(string);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @RequestMapping(value = "/getMarkerLocationById",method = RequestMethod.GET)
    public void getMarkerLocationById (@RequestParam String id) throws Exception {
        MarkeridEntity result = markeridRepo.findOne(id);
        String string;
        if (result == null) {
            string = JsonUtil.toJson(new SuccessStateObj(404,System.currentTimeMillis(),0,0,"数据发生错误","{}"));
        } else {
            string = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"数据查找成功",result));
        }
        try {
            response.getWriter().write(string);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //车子进出车库扫码
    @RequestMapping(value = "/userScanQR",method = RequestMethod.POST)
    public void userScanQR (@RequestParam String logid , @RequestParam String userid , @RequestParam String plate , @RequestParam String markerid) throws Exception {
        String resultStr;
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        Timestamp currentTimeAfterTwoHour = new Timestamp(System.currentTimeMillis()+7200000);
        List<UsercarEntity> findCarid = mUsercarRepository.findCarIdByPlate(plate,userid);
        String carid = findCarid.get(0).getCarId();
        UsermoneyEntity usermoney = mUsermoneyRepository.findUserMoneyLastLogById(userid);
        List<ParkinglogEntity> checkInOrOut = mUserParkingRepository.checkUserCarIsInTheMarker(userid,markerid,carid);
        if (checkInOrOut.size()==0){
            //进库--->判断是否预订---->判断车场是否有空位--->有没有余额
            List<UserorderEntity> checkIsOrderOntime = mUserOrderRepository.checkUserIsOrderOntime(userid,markerid,currentTime,currentTimeAfterTwoHour);
            if (checkIsOrderOntime.size()>0){
                //已预订车位
                if (usermoney.getRemain()<=0){
                    resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"已预订车位，但余额不足，请充值！"));
                }else {
                    ParkinglogEntity parkinglogEntity = new ParkinglogEntity();
                    parkinglogEntity.setLogId(logid);
                    parkinglogEntity.setUserId(userid);
                    parkinglogEntity.setMarkerId(markerid);
                    parkinglogEntity.setCarId(carid);
                    parkinglogEntity.setEnterTime(currentTime);
                    parkinglogEntity.setIsOrder(1);
                    mUserParkingRepository.saveAndFlush(parkinglogEntity);
                    ParkingstatusEntity parkingstatusEntity = mParkingStatusRepository.findOne(logid);
                    parkingstatusEntity.setStatus(1);//入库为1，出库为2
                    mParkingStatusRepository.saveAndFlush(parkingstatusEntity);
                    resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"已预订车位，扫码成功"));
                }
            }else {
                MarkerinfoEntity markerinfo = markerinfoRepo.findOne(markerid);
                List<UserorderEntity> checkMarkerOrderOntime = mUserOrderRepository.checkMarkerOrderOntime(markerid,currentTime,currentTimeAfterTwoHour);
                List<ParkinglogEntity> checkMarkerCarNum = mUserParkingRepository.checkTheMarkerParkingNum(markerid);
                if (markerinfo.getSpace()>checkMarkerCarNum.size()+checkMarkerOrderOntime.size()){
                    //有空位可以进入
                    ParkinglogEntity parkinglogEntity = new ParkinglogEntity();
                    parkinglogEntity.setLogId(logid);
                    parkinglogEntity.setUserId(userid);
                    parkinglogEntity.setMarkerId(markerid);
                    parkinglogEntity.setCarId(carid);
                    parkinglogEntity.setEnterTime(currentTime);
                    parkinglogEntity.setIsOrder(0);
                    mUserParkingRepository.saveAndFlush(parkinglogEntity);
                    ParkingstatusEntity parkingstatusEntity = mParkingStatusRepository.findOne(logid);
                    parkingstatusEntity.setStatus(1);//入库为1，出库为2
                    mParkingStatusRepository.saveAndFlush(parkingstatusEntity);
                    resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"扫码成功"));
                }else {
                    //车位已满
                    resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"车位已满"));
                }
            }
        }else {
            //出库---->计算进出库时间--->计费扣费---->余额还剩多少
            List<ParkinglogEntity> parkinglogList = mUserParkingRepository.checkCarParkingLog(markerid,carid);
            ParkinglogEntity parkinglogEntity = parkinglogList.get(0);
            parkinglogEntity.setLeaveTime(currentTime);
            parkinglogEntity.setIsComplete(1);
            mUserParkingRepository.saveAndFlush(parkinglogEntity);
            ParkingstatusEntity parkingstatusEntity = mParkingStatusRepository.findOne(logid);
            parkingstatusEntity.setStatus(2);//入库为1，出库为2
            mParkingStatusRepository.saveAndFlush(parkingstatusEntity);
            resultStr = JsonUtil.toJson(new SuccessStateObj(200,System.currentTimeMillis(),0,0,"离开成功"));
        }
        response.getWriter().write(resultStr);
    }



}
