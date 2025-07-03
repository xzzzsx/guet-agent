package com.atguigu.guliai.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AmapTools {

    @Value("${amap.api.key}")
    private String apiKey;

    @Tool(name = "maps_weather", description = "查询城市实时天气")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + apiKey + "&city=" + city;
        String response = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(response);

        // 解析返回数据
        JSONObject lives = json.getJSONArray("lives").getJSONObject(0);
        return String.format("%s天气：%s，温度%s°C，湿度%s%%，风向%s，风力%s级",
                lives.getStr("city"),
                lives.getStr("weather"),
                lives.getStr("temperature"),
                lives.getStr("humidity"),
                lives.getStr("winddirection"),
                lives.getStr("windpower"));
    }

    // 新增未来天气查询工具
    @Tool(name = "maps_future_weather", description = "查询城市未来天气，最多可查询未来4天天气")
    public String getFutureWeather(@ToolParam(description = "城市名称") String city) {
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + apiKey + "&city=" + city + "&extensions=all";
        String response = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(response);
        JSONArray forecasts = json.getJSONArray("forecasts");
        if (forecasts == null || forecasts.isEmpty()) {
            return "未找到该城市的未来天气信息";
        }

        JSONObject cityInfo = forecasts.getJSONObject(0);
        JSONArray casts = cityInfo.getJSONArray("casts");

        StringBuilder result = new StringBuilder();
        result.append(cityInfo.getStr("province")).append(cityInfo.getStr("city")).append("未来天气：\n");
        for (int i = 0; i < casts.size(); i++) {
            JSONObject cast = casts.getJSONObject(i);
            result.append(String.format("%s: %s，温度%s°C ~ %s°C，%s\n",
                    cast.getStr("date"),
                    cast.getStr("dayweather"),
                    cast.getStr("nighttemp"),
                    cast.getStr("daytemp"),
                    cast.getStr("daywind") + "风" + cast.getStr("daypower") + "级"));
        }
        return result.toString();
    }

    // 新增IP定位工具
    @Tool(name = "maps_ip_location", description = "通过IP地址查询位置信息")
    public String getLocationByIp() {
        String url = "https://restapi.amap.com/v3/ip?key=" + apiKey;
        String response = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(response);

        if (!"1".equals(json.getStr("status"))) {
            return "定位失败：" + json.getStr("info");
        }

        return String.format("当前位置：%s%s（IP：%s）",
                json.getStr("province"),
                json.getStr("city"),
                json.getStr("ip"));
    }
}