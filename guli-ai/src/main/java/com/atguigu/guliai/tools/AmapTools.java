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

    // 地理编码服务（地址转坐标）
    private String geocodeAddress(String address) {
        String url = "https://restapi.amap.com/v3/geocode/geo?key=" + apiKey + "&address=" + address;
        String response = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(response);

        if ("1".equals(json.getStr("status"))) {
            JSONArray geocodes = json.getJSONArray("geocodes");
            if (geocodes != null && !geocodes.isEmpty()) {
                return geocodes.getJSONObject(0).getStr("location");
            }
        }
        return null;
    }

    // 统一路线规划工具（支持所有交通方式）
    @Tool(name = "maps_route", description = "统一路线规划工具，支持驾车、公交、步行、骑行")
    public String getRoute(
            @ToolParam(description = "起点地址（城市+地点名称）") String origin,
            @ToolParam(description = "终点地址（城市+地点名称）") String destination,
            @ToolParam(description = "交通方式：driving（驾车）、transit（公交）、walking（步行）、bicycling（骑行）") String mode) {

        // 地理编码转换
        String originLocation = geocodeAddress(origin);
        String destLocation = geocodeAddress(destination);

        if (originLocation == null || destLocation == null) {
            return "无法解析地址，请确认地点名称是否正确";
        }

        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("origin", originLocation);
        params.put("destination", destLocation);

        String url;
        switch (mode.toLowerCase()) {
            case "transit":
                url = "https://restapi.amap.com/v3/direction/transit/integrated";
                params.put("city", origin.split("市")[0]); // 提取城市名
                params.put("strategy", 0); // 最快捷模式
                break;
            case "walking":
                url = "https://restapi.amap.com/v3/direction/walking";
                break;
            case "bicycling":
                url = "https://restapi.amap.com/v4/direction/bicycling";
                break;
            case "driving":
            default:
                url = "https://restapi.amap.com/v3/direction/driving";
                params.put("strategy", 10); // 速度最快
                break;
        }

        String response = HttpUtil.get(url, params);
        JSONObject json = JSONUtil.parseObj(response);

        if (!("1".equals(json.getStr("status")) || "0".equals(json.getStr("errcode")))) {
            return "路线规划失败：" + json.getStr("info");
        }

        // 统一解析结果
        String distance = "";
        String duration = "";
        String routeInfo = "";

        switch (mode.toLowerCase()) {
            case "driving":
            case "walking":
                JSONObject route = json.getJSONObject("route");
                JSONArray paths = route.getJSONArray("paths");
                JSONObject path = paths.getJSONObject(0);
                distance = path.getStr("distance");
                duration = path.getStr("duration");
                routeInfo = path.getStr("strategy");
                break;
            case "transit":
                route = json.getJSONObject("route");
                JSONArray transits = route.getJSONArray("transits");
                JSONObject transit = transits.getJSONObject(0);
                distance = transit.getStr("distance");
                duration = transit.getStr("duration");
                JSONArray segments = transit.getJSONArray("segments");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < segments.size(); i++) {
                    JSONObject segment = segments.getJSONObject(i);
                    sb.append(segment.getStr("instruction")).append(" → ");
                }
                routeInfo = sb.toString();
                break;
            case "bicycling":
                JSONObject data = json.getJSONObject("data");
                paths = data.getJSONArray("paths");
                path = paths.getJSONObject(0);
                distance = path.getStr("distance");
                duration = path.getStr("duration");
                break;
        }

        // 转换时间为分钟
        long minutes = Long.parseLong(duration) / 60;

        return String.format("%s路线：%s\n距离：%s米\n预计时间：%d分钟",
                getModeChinese(mode),
                routeInfo,
                distance,
                minutes);
    }

    // 获取中文交通方式
    private String getModeChinese(String mode) {
        switch (mode.toLowerCase()) {
            case "driving": return "驾车";
            case "transit": return "公交";
            case "walking": return "步行";
            case "bicycling": return "骑行";
            default: return "驾车";
        }
    }
}