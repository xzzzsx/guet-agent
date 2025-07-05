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

        JSONObject lives = json.getJSONArray("lives").getJSONObject(0);
        return String.format("%s天气：%s，温度%s°C，湿度%s%%，风向%s，风力%s级",
                lives.getStr("city"),
                lives.getStr("weather"),
                lives.getStr("temperature"),
                lives.getStr("humidity"),
                lives.getStr("winddirection"),
                lives.getStr("windpower"));
    }

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

    private Map<String, String> geocodeAddress(String address) {
        if (address.endsWith("市")) {
            address = address.substring(0, address.length() - 1);
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
                result.put("citycode", geo.getStr("citycode"));
                result.put("district", geo.getStr("district"));
                result.put("formatted", geo.getStr("formatted_address"));
                return result;
            }
        }
        return null;
    }

    @Tool(name = "maps_route", description = "路线规划工具，支持驾车和步行")
    public String getRoute(
            @ToolParam(description = "起点地址（城市+地点名称）") String origin,
            @ToolParam(description = "终点地址（城市+地点名称）") String destination,
            @ToolParam(description = "交通方式：driving（驾车）、walking（步行）") String mode) {

        try {
            System.out.println("🚗🚗🚶🚶 开始处理路线查询请求");
            System.out.println("📍 起点: " + origin);
            System.out.println("📍 终点: " + destination);
            System.out.println("🚗🚗 交通方式: " + mode);

            Map<String, String> originGeo = geocodeAddress(origin);
            Map<String, String> destGeo = geocodeAddress(destination);

            if (originGeo == null || destGeo == null) {
                System.err.println("⚠️ 无法解析地点，请尝试输入更具体的位置");
                return "无法解析地点，请尝试输入更具体的位置，如'梧州市政府'或'广州市中心'";
            }

            System.out.println("📍 起点地理编码结果: " + originGeo);
            System.out.println("📍 终点地理编码结果: " + destGeo);

            // 保存原始地址用于错误提示
            String originalOrigin = originGeo.getOrDefault("formatted", origin);
            String originalDest = destGeo.getOrDefault("formatted", destination);

            return getRouteInternal(
                    originGeo.get("location"),
                    destGeo.get("location"),
                    mode,
                    originGeo.get("city"),
                    originGeo.get("citycode"),
                    originGeo.get("district"),
                    destGeo.get("city"),
                    destGeo.get("citycode"),
                    destGeo.get("district"),
                    originalOrigin,
                    originalDest
            );
        } catch (Exception e) {
            System.err.println("❌❌ 路线查询异常: " + e.getMessage());
            return "路线查询异常：" + e.getMessage();
        }
    }

    private String getRouteInternal(String originLocation, String destLocation, String mode,
                                    String originCity, String originCityCode, String originDistrict,
                                    String destCity, String destCityCode, String destDistrict,
                                    String originalOrigin, String originalDest) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("origin", originLocation);
        params.put("destination", destLocation);

        String url;
        switch (mode.toLowerCase()) {
            case "walking":
                url = "https://restapi.amap.com/v5/direction/walking";
                System.out.println("🚶🚶 步行API请求URL: " + url);
                break;
            case "driving":
            default:
                url = "https://restapi.amap.com/v5/direction/driving";
                params.put("strategy", 32);
                System.out.println("🚗🚗 驾车API请求URL: " + url);
                break;
        }

        try {
            System.out.println("📡📡 发送请求到高德API...");
            String response = HttpUtil.get(url, params);
            System.out.println("✅ 收到高德API响应");
            System.out.println("📄📄 原始API响应: " + (response.length() > 500 ? response.substring(0, 500) + "..." : response));

            JSONObject json = JSONUtil.parseObj(response);

            String status = json.getStr("status");
            String errcode = json.getStr("errcode");
            String info = json.getStr("info", "未知错误");

            System.out.println("🔍🔍 API状态: status=" + status + ", errcode=" + errcode + ", info=" + info);

            if (!("1".equals(status) || "10000".equals(errcode) || "0".equals(errcode))) {
                System.err.println("❌❌ 高德API返回错误: " + info);
                return handleRouteError(json, mode);
            }

            // 传递原始地址
            return parseRouteResult(json, mode,
                    originCity + originDistrict,
                    destCity + destDistrict,
                    originalOrigin,
                    originalDest
            );
        } catch (Exception e) {
            System.err.println("❌❌ 路线解析异常: " + e.getMessage());
            return "路线解析异常：" + e.getMessage();
        }
    }

    private String parseRouteResult(JSONObject json, String mode, String originName, String destName,
                                    String originalOrigin, String originalDest) {
        try {
            StringBuilder routeInfo = new StringBuilder();
            routeInfo.append(String.format("从【%s】到【%s】的%s路线：\n",
                    originalOrigin, originalDest, getModeChinese(mode)));

            switch (mode.toLowerCase()) {
                case "driving":
                    return parseDrivingRoute(json, routeInfo);
                case "walking":
                    return parseWalkingRoute(json, routeInfo);
                default:
                    return "不支持的交通方式：" + mode;
            }
        } catch (Exception e) {
            System.err.println("❌❌ 路线解析失败: " + e.getMessage());
            return "路线解析失败：" + e.getMessage();
        }
    }

    private String parseDrivingRoute(JSONObject json, StringBuilder routeInfo) {
        JSONObject route = json.getJSONObject("route");
        if (route == null) {
            return "未找到驾车路线信息";
        }

        JSONArray paths = route.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            return "未找到驾车路线";
        }

        JSONObject path = paths.getJSONObject(0);

        // 安全获取字段值
        String distance = path.getStr("distance", "未知");
        String duration = path.getStr("duration", "未知");
        String strategy = path.getStr("strategy", "未知");

        // 格式化距离和耗时
        String formattedDistance = formatDistance(distance);
        String formattedDuration = formatDuration(duration);

        routeInfo.append("🗺🗺 主要途径：").append(getMainRoads(path)).append("\n");
        routeInfo.append("📏📏 总距离：").append(formattedDistance).append("\n");
        routeInfo.append("⏱⏱⏱ 预计耗时：").append(formattedDuration).append("\n");
        routeInfo.append("🚗🚗 路线策略：").append(strategy).append("\n");

        JSONArray steps = path.getJSONArray("steps");
        if (steps != null && !steps.isEmpty()) {
            routeInfo.append("\n📍 详细路线指引：\n");
            for (int i = 0; i < steps.size(); i++) {
                JSONObject step = steps.getJSONObject(i);
                routeInfo.append(i+1).append(". ")
                        .append(step.getStr("instruction", "无指引信息").replaceAll("<[^>]+>", ""))
                        .append("\n");
            }
        } else {
            routeInfo.append("\n⚠️ 无详细路线指引信息\n");
        }

        return routeInfo.toString();
    }

    private String parseWalkingRoute(JSONObject json, StringBuilder routeInfo) {
        JSONObject data = json.getJSONObject("route");
        if (data == null) {
            return "未找到步行路线信息";
        }

        JSONArray pathsArr = data.getJSONArray("paths");
        if (pathsArr == null || pathsArr.isEmpty()) {
            return "未找到步行路线";
        }

        JSONObject pathObj = pathsArr.getJSONObject(0);

        // 安全获取字段值
        String distance = pathObj.getStr("distance", "未知");
        String duration = pathObj.getStr("duration", "未知");

        routeInfo.append("🚶🚶 步行路线：\n");
        routeInfo.append("📏📏 总距离：").append(formatDistance(distance)).append("\n");
        routeInfo.append("⏱⏱⏱ 预计耗时：").append(formatDuration(duration)).append("\n");

        JSONArray stepsArr = pathObj.getJSONArray("steps");
        if (stepsArr != null && !stepsArr.isEmpty()) {
            routeInfo.append("\n📍 详细路线指引：\n");
            for (int i = 0; i < stepsArr.size(); i++) {
                JSONObject step = stepsArr.getJSONObject(i);
                routeInfo.append(i+1).append(". ")
                        .append(step.getStr("instruction", "无指引信息").replaceAll("<[^>]+>", ""))
                        .append("\n");
            }
        } else {
            routeInfo.append("\n⚠️ 无详细路线信息\n");
        }

        return routeInfo.toString();
    }

    private String getMainRoads(JSONObject path) {
        try {
            JSONArray tmcs = path.getJSONArray("tmcs");
            if (tmcs != null && !tmcs.isEmpty()) {
                StringBuilder roads = new StringBuilder();
                for (int i = 0; i < tmcs.size(); i++) {
                    JSONObject tmc = tmcs.getJSONObject(i);
                    String roadName = tmc.getStr("road_name", "未知道路");
                    roads.append(roadName);
                    if (i < tmcs.size() - 1) roads.append(" → ");
                }
                return roads.toString();
            }
            return path.getStr("route", "未获取道路信息").replace(";", " → ");
        } catch (Exception e) {
            return "主要道路信息提取失败";
        }
    }

    // 重载方法处理字符串类型的距离
    private String formatDistance(String metersStr) {
        try {
            long meters = Long.parseLong(metersStr);
            return formatDistance(meters);
        } catch (NumberFormatException e) {
            return metersStr + "米";
        }
    }

    private String formatDistance(long meters) {
        if (meters > 1000) {
            return String.format("%.1f公里", meters / 1000.0);
        }
        return meters + "米";
    }

    // 重载方法处理字符串类型的耗时
    private String formatDuration(String secondsStr) {
        try {
            long seconds = Long.parseLong(secondsStr);
            return formatDuration(seconds);
        } catch (NumberFormatException e) {
            return secondsStr + "秒";
        }
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        if (minutes > 60) {
            return String.format("%d小时%d分钟", minutes / 60, minutes % 60);
        }
        return minutes + "分钟";
    }

    private String handleRouteError(JSONObject json, String mode) {
        String info = json.getStr("info", "未知错误");
        String infocode = json.getStr("infocode", "未知错误码");
        return "路线规划失败：" + info + "（错误码：" + infocode + ")";
    }

    private String getModeChinese(String mode) {
        switch (mode.toLowerCase()) {
            case "driving":
                return "驾车";
            case "walking":
                return "步行";
            default:
                return "驾车";
        }
    }
}