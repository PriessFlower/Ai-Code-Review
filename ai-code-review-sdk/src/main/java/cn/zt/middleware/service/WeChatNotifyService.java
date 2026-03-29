package cn.zt.middleware.service;

import cn.zt.middleware.config.WeChatProperties;
import cn.zt.middleware.model.TemplateItem;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class WeChatNotifyService {

    private static final String GET_ACCESSTOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final String SEND_TEMPLATEMSG_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s";

    private final WeChatProperties weChatProperties;
    private final RestClient restClient;

    public WeChatNotifyService(WeChatProperties weChatProperties, RestClient.Builder restClientBuilder) {
        this.weChatProperties = weChatProperties;
        this.restClient = restClientBuilder.build();
    }

    public void sendReviewNotification(String projectName, String reviewUrl) {
        String accessToken = getAccessToken();
        sendTemplateMessage(accessToken, projectName, reviewUrl);
    }

    private String getAccessToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credential");
        formData.add("appid", weChatProperties.getAppId());
        formData.add("secret",weChatProperties.getSecret());
        formData.add("force_refresh", "false");

        String response = restClient.post()
                .uri(GET_ACCESSTOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED) // 关键：设置内容类型为表单
                .accept(MediaType.APPLICATION_JSON)
                .body(formData) // 传递表单数据
                .retrieve()
                .body(String.class);

        String token = JSON.parseObject(response).getString("access_token");
        log.info("微信 access_token 获取成功");
        return token;
    }

    private void sendTemplateMessage(String accessToken, String projectName, String reviewUrl) {
        String url = String.format(SEND_TEMPLATEMSG_URL, accessToken);

        JSONObject data = new JSONObject();
        data.fluentPut("project", new TemplateItem(projectName, "#173177"))
                .fluentPut("review", new TemplateItem(reviewUrl, "#173177"));

        JSONObject body = new JSONObject();
        body.fluentPut("touser", weChatProperties.getToUser())
                .fluentPut("template_id", weChatProperties.getTemplateId())
                .fluentPut("topcolor", "#FF0000")
                .fluentPut("data", data);

        String response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(body.toJSONString())
                .retrieve()
                .body(String.class);

        log.info("微信模板消息发送结果: {}", response);
    }
}
