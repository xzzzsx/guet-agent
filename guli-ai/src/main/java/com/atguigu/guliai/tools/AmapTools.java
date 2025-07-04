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

    // 地理编码服务（地址转坐标）- 增强版
    private Map<String, String> geocodeAddress(String address) {
        // 智能补充"市"后缀提高解析成功率
        if (!address.endsWith("市") && !address.contains(" ")) {
            address += "市";
        }

        String url = "https://restapi.amap.com/v3/geocode/geo?key=" + apiKey + "&address=" + address;
        String response = HttpUtil.get(url);
        JSONObject json = JSONUtil.parseObj(response);

        if ("1".equals(json.getStr("status"))) {
            JSONArray geocodes = json.getJSONArray("geocodes");
            if (geocodes != null && !geocodes.isEmpty()) {
                JSONObject geo = geocodes.getJSONObject(0);
                Map<String, String> result = new HashMap<>();
                result.put("location", geo.getStr("location"));
                result.put("city", geo.getStr("city"));
                return result;
            }
        }
        return null;
    }

    // 统一路线规划工具（支持所有交通方式）- 优化版
    @Tool(name = "maps_route", description = "统一路线规划工具，支持驾车、公交、步行、骑行")
    public String getRoute(
            @ToolParam(description = "起点地址（城市+地点名称）") String origin,
            @ToolParam(description = "终点地址（城市+地点名称）") String destination,
            @ToolParam(description = "交通方式：driving（驾车）、transit（公交）、walking（步行）、bicycling（骑行）") String mode) {

        // 智能解析起点和终点
        Map<String, String> originGeo = geocodeAddress(origin);
        Map<String, String> destGeo = geocodeAddress(destination);

        if (originGeo == null || destGeo == null) {
            return "无法解析地点，请尝试输入更具体的位置，如'梧州市政府'或'广州市中心'";
        }

        String originLocation = originGeo.get("location");
        String destLocation = destGeo.get("location");
        String originCity = originGeo.get("city");
        String destCity = destGeo.get("city");

        // 自动处理跨城市路线
        boolean crossCity = !originCity.equals(destCity);
        if (crossCity) {
            // 跨城市自动切换为驾车
            if (!"driving".equalsIgnoreCase(mode)) {
                mode = "driving";
                return getRouteInternal(originLocation, destLocation, mode) +
                        "\n\n提示：跨城市路线已自动切换为驾车模式";
            }
        }

        return getRouteInternal(originLocation, destLocation, mode);
    }

    // 内部路线规划实现
    private String getRouteInternal(String originLocation, String destLocation, String mode) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("origin", originLocation);
        params.put("destination", destLocation);

        String url;
        switch (mode.toLowerCase()) {
            case "transit":
                url = "https://restapi.amap.com/v3/direction/transit/integrated";
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

        String status = json.getStr("status");
        String errcode = json.getStr("errcode");
        if (!("1".equals(status) || "10000".equals(errcode) || "0".equals(errcode))) {
            return handleRouteError(json, mode);
        }

        return parseRouteResult(json, mode);
    }

    // 统一解析路线结果
    private String parseRouteResult(JSONObject json, String mode) {
        String distance = "";
        String duration = "";
        StringBuilder routeInfo = new StringBuilder();

        try {
            switch (mode.toLowerCase()) {
                case "driving":
                    JSONObject route = json.getJSONObject("route");
                    JSONArray paths = route.getJSONArray("paths");
                    JSONObject path = paths.getJSONObject(0);
                    distance = path.getStr("distance");
                    duration = path.getStr("duration");
                    routeInfo.append("推荐路线：").append(path.getStr("strategy"));
                    break;

                case "transit":
                    route = json.getJSONObject("route");
                    JSONArray transits = route.getJSONArray("transits");
                    if (transits == null || transits.isEmpty()) {
                        return "未找到公交路线，建议使用其他交通方式";
                    }

                    JSONObject bestTransit = transits.getJSONObject(0);
                    distance = bestTransit.getStr("distance");
                    duration = bestTransit.getStr("duration");

                    JSONArray segments = bestTransit.getJSONArray("segments");
                    for (int i = 0; i < segments.size(); i++) {
                        JSONObject segment = segments.getJSONObject(i);
                        String instruction = segment.getStr("instruction")
                                .replace("→", "->")
                                .replace("，", ", ");
                        routeInfo.append("\n➜ ").append(instruction);
                    }
                    break;

                case "walking":
                case "bicycling":
                    JSONObject data = json.getJSONObject("data");
                    paths = data.getJSONArray("paths");
                    JSONObject pathObj = paths.getJSONObject(0);
                    distance = pathObj.getStr("distance");
                    duration = pathObj.getStr("duration");

                    JSONArray steps = pathObj.getJSONArray("steps");
                    for (int i = 0; i < steps.size(); i++) {
                        JSONObject step = steps.getJSONObject(i);
                        routeInfo.append("\n➜ ").append(step.getStr("instruction"));
                    }
                    break;
            }

            // 转换时间为分钟
            long minutes = Long.parseLong(duration) / 60;
            return String.format("%s路线：%s\n距离：%s米\n预计时间：%d分钟",
                    getModeChinese(mode),
                    routeInfo.toString(),
                    distance,
                    minutes);

        } catch (Exception e) {
            return "路线解析失败：" + e.getMessage();
        }
    }

    // 统一错误处理
    private String handleRouteError(JSONObject json, String mode) {
        String info = json.getStr("info");
        String infocode = json.getStr("infocode");

        // 特殊处理跨城公交
        if ("transit".equalsIgnoreCase(mode) && "30001".equals(infocode)) {
            return "跨城市公交路线需换乘，建议使用驾车或火车/高铁等交通工具";
        }

        // 步行/骑行距离过远
        if (("walking".equalsIgnoreCase(mode) || "bicycling".equalsIgnoreCase(mode))
                && info.contains("距离过远")) {
            return String.format("%s距离过远（超过100公里），建议选择其他交通方式", getModeChinese(mode));
        }

        return "路线规划失败：" + info;
    }

    // 获取中文交通方式（保持不变）
    private String getModeChinese(String mode) {
        switch (mode.toLowerCase()) {
            case "driving":
                return "驾车";
            case "transit":
                return "公交";
            case "walking":
                return "步行";
            case "bicycling":
                return "骑行";
            default:
                return "驾车";
        }
    }
}
