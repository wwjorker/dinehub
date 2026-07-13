package com.dinehub.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dinehub.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 微信支付工具类
 */
@Component
public class WeChatPayUtil {

    //微信支付下单接口地址
    public static final String JSAPI = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";

    //申请退款接口地址
    public static final String REFUNDS = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";

    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 获取调用微信接口的客户端工具对象
     *
     * @return
     */
    private CloseableHttpClient getClient() throws IOException {
        PrivateKey merchantPrivateKey;
        X509Certificate x509Certificate;
        try (FileInputStream merchantKey = new FileInputStream(new File(weChatProperties.getPrivateKeyFilePath()));
             FileInputStream platformCert = new FileInputStream(new File(weChatProperties.getWeChatPayCertFilePath()))) {
            merchantPrivateKey = PemUtil.loadPrivateKey(merchantKey);
            x509Certificate = PemUtil.loadCertificate(platformCert);
        }
        List<X509Certificate> wechatPayCertificates = Arrays.asList(x509Certificate);
        return WechatPayHttpClientBuilder.create()
                .withMerchant(weChatProperties.getMchid(), weChatProperties.getMchSerialNo(), merchantPrivateKey)
                .withWechatPay(wechatPayCertificates)
                .build();
    }

    /**
     * 发送post方式请求
     *
     * @param url
     * @param body
     * @return
     */
    private String post(String url, String body) throws Exception {
        HttpPost request = new HttpPost(url);
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader("Wechatpay-Serial", weChatProperties.getMchSerialNo());
        request.setEntity(new StringEntity(body, "UTF-8"));
        try (CloseableHttpClient httpClient = getClient();
             CloseableHttpResponse response = httpClient.execute(request)) {
            return requireSuccess(response);
        }
    }

    /**
     * 发送get方式请求
     *
     * @param url
     * @return
     */
    private String get(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        request.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        request.addHeader("Wechatpay-Serial", weChatProperties.getMchSerialNo());
        try (CloseableHttpClient httpClient = getClient();
             CloseableHttpResponse response = httpClient.execute(request)) {
            return requireSuccess(response);
        }
    }

    private String requireSuccess(CloseableHttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        String responseBody = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        if (statusCode < 200 || statusCode >= 300) {
            throw new WeChatPayHttpException(statusCode);
        }
        return responseBody;
    }

    /**
     * jsapi下单
     *
     * @param orderNum    商户订单号
     * @param total       总金额
     * @param description 商品描述
     * @param openid      微信用户的openid
     * @return
     */
    private String jsapi(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("appid", weChatProperties.getAppid());
        jsonObject.put("mchid", weChatProperties.getMchid());
        jsonObject.put("description", description);
        jsonObject.put("out_trade_no", orderNum);
        jsonObject.put("notify_url", weChatProperties.getNotifyUrl());

        JSONObject amount = new JSONObject();
        amount.put("total", total.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put("currency", "CNY");

        jsonObject.put("amount", amount);

        JSONObject payer = new JSONObject();
        payer.put("openid", openid);

        jsonObject.put("payer", payer);

        String body = jsonObject.toJSONString();
        return post(JSAPI, body);
    }

    /**
     * 小程序支付
     *
     * @param orderNum    商户订单号
     * @param total       金额，单位 元
     * @param description 商品描述
     * @param openid      微信用户的openid
     * @return
     */
    public JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        //统一下单，生成预支付交易单
        String bodyAsString = jsapi(orderNum, total, description, openid);
        //解析返回结果
        JSONObject jsonObject = JSON.parseObject(bodyAsString);
        String prepayId = jsonObject.getString("prepay_id");
        if (prepayId != null) {
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr = RandomStringUtils.randomNumeric(32);
            ArrayList<Object> list = new ArrayList<>();
            list.add(weChatProperties.getAppid());
            list.add(timeStamp);
            list.add(nonceStr);
            list.add("prepay_id=" + prepayId);
            //二次签名，调起支付需要重新签名
            StringBuilder stringBuilder = new StringBuilder();
            for (Object o : list) {
                stringBuilder.append(o).append("\n");
            }
            String signMessage = stringBuilder.toString();
            byte[] message = signMessage.getBytes();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(PemUtil.loadPrivateKey(new FileInputStream(new File(weChatProperties.getPrivateKeyFilePath()))));
            signature.update(message);
            String packageSign = Base64.getEncoder().encodeToString(signature.sign());

            //构造数据给微信小程序，用于调起微信支付
            JSONObject jo = new JSONObject();
            jo.put("timeStamp", timeStamp);
            jo.put("nonceStr", nonceStr);
            jo.put("package", "prepay_id=" + prepayId);
            jo.put("signType", "RSA");
            jo.put("paySign", packageSign);

            return jo;
        }
        return jsonObject;
    }

    /**
     * 申请退款
     *
     * @param outTradeNo    商户订单号
     * @param outRefundNo   商户退款单号
     * @param refund        退款金额
     * @param total         原订单金额
     * @return
     */
    public RefundStatus refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("out_trade_no", outTradeNo);
        jsonObject.put("out_refund_no", outRefundNo);

        JSONObject amount = new JSONObject();
        amount.put("refund", refund.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put("total", total.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put("currency", "CNY");

        jsonObject.put("amount", amount);
        jsonObject.put("notify_url", weChatProperties.getRefundNotifyUrl());

        String body = jsonObject.toJSONString();

        //调用申请退款接口
        return parseRefundStatus(post(REFUNDS, body));
    }

    /**
     * Querying by the fixed merchant refund number resolves network-unknown
     * outcomes without creating another refund instruction.
     */
    public RefundStatus queryRefund(String outRefundNo) throws Exception {
        try {
            return parseRefundStatus(get(REFUNDS + "/" + outRefundNo));
        } catch (WeChatPayHttpException ex) {
            if (ex.getStatusCode() == 404) {
                return RefundStatus.NOT_FOUND;
            }
            throw ex;
        }
    }

    private RefundStatus parseRefundStatus(String body) throws IOException {
        try {
            String status = JSON.parseObject(body).getString("status");
            if (status == null) {
                throw new IOException("refund response missing status");
            }
            return RefundStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new IOException("unknown refund status", ex);
        } catch (RuntimeException ex) {
            throw new IOException("invalid refund response", ex);
        }
    }

    public enum RefundStatus {
        SUCCESS, PROCESSING, CLOSED, ABNORMAL, NOT_FOUND
    }

    public static class WeChatPayHttpException extends IOException {
        private final int statusCode;

        public WeChatPayHttpException(int statusCode) {
            super("WeChat Pay HTTP status " + statusCode);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
