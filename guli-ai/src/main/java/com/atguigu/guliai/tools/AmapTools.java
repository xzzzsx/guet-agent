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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    @Tool(name = "maps_route", description = "路线规划工具，支持驾车和步行，必须返回包含地图的路线信息")
    public String getRoute(
            @ToolParam(description = "起点地址（城市+地点名称）") String origin,
            @ToolParam(description = "终点地址（城市+地点名称）") String destination,
            @ToolParam(description = "交通方式：driving（驾车）、walking（步行）") String mode) {

        try {
            System.out.println("🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶 开始处理路线查询请求");
            System.out.println("📍 起点: " + origin);
            System.out.println("📍 终点: " + destination);
            System.out.println("🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗 交通方式: " + mode);

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
            System.err.println("❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌ 路线查询异常: " + e.getMessage());
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
                System.out.println("🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶🚶 步行API请求URL: " + url);
                break;
            case "driving":
            default:
                url = "https://restapi.amap.com/v5/direction/driving";
                params.put("strategy", 32);
                System.out.println("🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗🚗 驾车API请求URL: " + url);
                break;
        }

        try {
            System.out.println("📡📡📡📡📡📡📡📡📡📡📡📡📡📡📡📡 发送请求到高德API...");
            String response = HttpUtil.get(url, params);
            System.out.println("✅ 收到高德API响应");
            System.out.println("📄📄📄📄📄📄📄📄📄📄📄📄📄📄📄📄 原始API响应: " + (response.length() > 500 ? response.substring(0, 500) + "..." : response));

            JSONObject json = JSONUtil.parseObj(response);

            String status = json.getStr("status");
            String errcode = json.getStr("errcode");
            String info = json.getStr("info", "未知错误");

            System.out.println("🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍🔍 API状态: status=" + status + ", errcode=" + errcode + ", info=" + info);

            if (!("1".equals(status) || "10000".equals(errcode) || "0".equals(errcode))) {
                System.err.println("❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌ 高德API返回错误: " + info);
                return handleRouteError(json, mode);
            }

            return parseRouteResult(json, mode,
                    originCity + originDistrict,
                    destCity + destDistrict,
                    originalOrigin,
                    originalDest,
                    originLocation,
                    destLocation
            );
        } catch (Exception e) {
            System.err.println("❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌ 路线解析异常: " + e.getMessage());
            return "路线解析异常：" + e.getMessage();
        }
    }

    private String parseRouteResult(JSONObject json, String mode,
                                    String originName, String destName,
                                    String originalOrigin, String originalDest,
                                    String originLocation, String destLocation) {
        try {
            StringBuilder routeInfo = new StringBuilder();
            routeInfo.append(String.format("从【%s】到【%s】的%s路线：\n",
                    originalOrigin, originalDest, getModeChinese(mode)));

            switch (mode.toLowerCase()) {
                case "driving":
                    return parseDrivingRoute(json, routeInfo, originalOrigin, originalDest, originLocation, destLocation);
                case "walking":
                    return parseWalkingRoute(json, routeInfo, originalOrigin, originalDest, originLocation, destLocation);
                default:
                    return "不支持的交通方式：" + mode;
            }
        } catch (Exception e) {
            System.err.println("❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌❌ 路线解析失败: " + e.getMessage());
            return "路线解析失败：" + e.getMessage();
        }
    }

    // 驾车路线解析
    // 驾车路线解析
    private String parseDrivingRoute(JSONObject json, StringBuilder routeInfo,
                                     String originalOrigin, String originalDest,
                                     String originLocation, String destLocation) {
        JSONObject route = json.getJSONObject("route");
        if (route == null) {
            return "未找到驾车路线信息";
        }

        JSONArray paths = route.getJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            return "未找到驾车路线";
        }

        JSONObject path = paths.getJSONObject(0);

        String distance = path.getStr("distance", "未知");
        String duration = path.getStr("duration", "未知");
        String strategy = path.getStr("strategy", "未知");

        routeInfo.append("🗺 主要途径：").append(getMainRoads(path)).append("\n");
        routeInfo.append("📏 总距离：").append(formatDistance(distance)).append("\n");
        routeInfo.append("⏱ 预计耗时：").append(formatDuration(duration)).append("\n");
        routeInfo.append("🚗 路线策略：").append(strategy).append("\n");

        JSONArray steps = path.getJSONArray("steps");
        if (steps != null && !steps.isEmpty()) {
            routeInfo.append("\n📍 详细路线指引：\n");
            for (int i = 0; i < steps.size(); i++) {
                JSONObject step = steps.getJSONObject(i);
                routeInfo.append(i + 1).append(". ")
                        .append(step.getStr("instruction", "无指引信息").replaceAll("<[^>]+>", ""))
                        .append("\n");
            }
        } else {
            routeInfo.append("\n⚠️ 无详细路线指引信息\n");
        }

        // 使用地理编码的坐标生成简单地图
        String staticMapImg = generateSimpleMap(originalOrigin, originalDest, originLocation, destLocation);
        if (!staticMapImg.isEmpty()) {
            routeInfo.append(staticMapImg);
        } else {
            routeInfo.append("\n\n⚠️ 无法生成路线地图");
        }

        // 生成地图URL（不包含HTML标签）
        String mapUrl = generateSimpleMap(originalOrigin, originalDest, originLocation, destLocation);
        if (!mapUrl.isEmpty()) {
            // 只返回纯URL，不加任何描述
            routeInfo.append("\n\n").append(mapUrl);
        }

        return routeInfo.toString();
    }

    // 步行路线解析
    // 步行路线解析
    private String parseWalkingRoute(JSONObject json, StringBuilder routeInfo,
                                     String originalOrigin, String originalDest,
                                     String originLocation, String destLocation) {
        JSONObject data = json.getJSONObject("route");
        if (data == null) {
            return "未找到步行路线信息";
        }

        JSONArray pathsArr = data.getJSONArray("paths");
        if (pathsArr == null || pathsArr.isEmpty()) {
            return "未找到步行路线";
        }

        JSONObject pathObj = pathsArr.getJSONObject(0);

        String distance = pathObj.getStr("distance", "未知");
        String duration = pathObj.getStr("duration", "未知");

        routeInfo.append("🚶 步行路线：\n");
        routeInfo.append("📏 总距离：").append(formatDistance(distance)).append("\n");
        routeInfo.append("⏱ 预计耗时：").append(formatDuration(duration)).append("\n");

        JSONArray stepsArr = pathObj.getJSONArray("steps");
        if (stepsArr != null && !stepsArr.isEmpty()) {
            routeInfo.append("\n📍 详细路线指引：\n");
            for (int i = 0; i < stepsArr.size(); i++) {
                JSONObject step = stepsArr.getJSONObject(i);
                routeInfo.append(i + 1).append(". ")
                        .append(step.getStr("instruction", "无指引信息").replaceAll("<[^>]+>", ""))
                        .append("\n");
            }
        } else {
            routeInfo.append("\n⚠️ 无详细路线信息\n");
        }

        // 使用地理编码的坐标生成简单地图
        String staticMapImg = generateSimpleMap(originalOrigin, originalDest, originLocation, destLocation);
        if (!staticMapImg.isEmpty()) {
            routeInfo.append(staticMapImg);
        } else {
            routeInfo.append("\n\n⚠️ 无法生成路线地图");
        }

        // 生成地图URL（不包含HTML标签）
        String mapUrl = generateSimpleMap(originalOrigin, originalDest, originLocation, destLocation);
        if (!mapUrl.isEmpty()) {
            // 只返回纯URL，不加任何描述
            routeInfo.append("\n\n").append(mapUrl);
        }

        return routeInfo.toString();
    }

    // 生成简单地图（仅显示起点和终点）
    // 修改generateSimpleMap方法
    private String generateSimpleMap(String originName, String destName,
                                     String originLocation, String destLocation) {
        try {
            // 检查API密钥
            if (apiKey == null || apiKey.isEmpty()) {
                System.err.println("❌ 高德API密钥未配置");
                return "";
            }

            // 构建基础URL
            String baseUrl = "https://restapi.amap.com/v3/staticmap";

            // 调试输出坐标信息
            System.out.println("📍 起点坐标: " + originLocation);
            System.out.println("📍 终点坐标: " + destLocation);

            // 按照官方格式构建标记参数
            // 格式: markers=size,color,label:lng,lat|size,color,label:lng,lat
            String markersParam = "mid,0xFF0000,A:" + originLocation + "|mid,0x00FF00,B:" + destLocation;

            // 构建参数
            Map<String, String> params = new LinkedHashMap<>();
            params.put("markers", markersParam);
            params.put("key", apiKey);

            // 打印完整参数
            System.out.println("🔧 完整参数: " + params);

            // 构建完整URL
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            urlBuilder.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append(entry.getKey())
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()))
                        .append("&");
            }
            String mapUrl = urlBuilder.substring(0, urlBuilder.length() - 1); // 移除最后一个&

            // 打印调试信息
            System.out.println("🖼️ 生成地图URL: " + mapUrl);

            // 验证URL
            return validateMapUrl(mapUrl);
        } catch (Exception e) {
            System.err.println("❌ 生成简单地图URL失败: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    // 验证地图URL
    private String validateMapUrl(String mapUrl) {
        try {
            System.out.println("🔍 开始验证地图URL: " + mapUrl);
            String response = HttpUtil.get(mapUrl);

            // 检查是否为图片响应
            if (response.startsWith("�PNG") || response.contains("JFIF") ||
                    response.startsWith("GIF") || response.startsWith("BM")) {
                System.out.println("✅ 地图URL验证成功 - 返回图片数据");
                return mapUrl;
            }

            // 尝试解析错误响应
            try {
                JSONObject json = JSONUtil.parseObj(response);
                if (json.containsKey("status")) {
                    String status = json.getStr("status");
                    String info = json.getStr("info", "未知错误");
                    String infocode = json.getStr("infocode", "未知错误码");

                    System.err.println("❌ 高德API返回错误: status=" + status +
                            ", info=" + info + ", infocode=" + infocode);

                    return "地图生成失败: " + info + "(" + infocode + ")";
                }
            } catch (Exception e) {
                System.err.println("⚠️ 地图响应解析失败: " + e.getMessage());
            }

            System.err.println("⚠️ 地图URL验证失败 - 未知响应: " +
                    (response.length() > 100 ? response.substring(0, 100) + "..." : response));
            return "地图生成失败: 未知错误";
        } catch (Exception e) {
            System.err.println("❌ 地图URL测试失败: " + e.getMessage());
            return "地图生成失败: " + e.getMessage();
        }
    }

    // 测试地图URL有效性
    private boolean testMapUrl(String mapUrl) {
        try {
            String response = HttpUtil.get(mapUrl);
            // 检查响应是否为图片
            if (response.startsWith("�PNG") || response.contains("JFIF")) {
                System.out.println("✅ 地图URL验证成功");
                return true;
            }

            // 解析可能的错误响应
            try {
                JSONObject json = JSONUtil.parseObj(response);
                String status = json.getStr("status");
                if ("0".equals(status)) {
                    System.err.println("❌ 高德API返回错误: " +
                            json.getStr("info") + "(" + json.getStr("infocode") + ")");
                }
            } catch (Exception e) {
                System.err.println("⚠️ 地图响应解析失败: " + e.getMessage());
            }
            return false;
        } catch (Exception e) {
            System.err.println("❌ 地图URL测试失败: " + e.getMessage());
            return false;
        }
    }


    // 尝试从steps重建polyline
    private String tryBuildPolylineFromSteps(JSONObject path, String originName, String destName) {
        System.out.println("⚠️ 尝试从steps重建polyline...");

        JSONArray steps = path.getJSONArray("steps");
        if (steps == null || steps.isEmpty()) {
            System.err.println("❌ 无法从steps重建polyline：steps为空");
            return "";
        }

        StringBuilder polylineBuilder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String stepPolyline = step.getStr("polyline", "");
            if (!stepPolyline.isEmpty()) {
                if (polylineBuilder.length() > 0) {
                    polylineBuilder.append(";");
                }
                polylineBuilder.append(stepPolyline);
            }
        }

        String polyline = polylineBuilder.toString();
        if (polyline.isEmpty()) {
            System.err.println("❌ 无法从steps重建polyline：所有step的polyline都为空");
            return "";
        }

        System.out.println("🔄 从steps重建的polyline: " + (polyline.length() > 100 ? polyline.substring(0, 100) + "..." : polyline));
        return buildMapImage(polyline, originName, destName);
    }

    // 构建地图图片
    private String buildMapImage(String polyline, String originName, String destName) {
        try {
            // 1. 简化路径点（最多30个点）
            List<String> simplifiedPoints = simplifyPolyline(polyline, 30);
            if (simplifiedPoints.isEmpty()) {
                System.err.println("❌ 简化后路径点为空");
                return "";
            }

            // 调试日志
            System.out.println("🗺️ 简化后路径点数量: " + simplifiedPoints.size());
            System.out.println("🗺️ 首点: " + simplifiedPoints.get(0));
            System.out.println("🗺️ 末点: " + simplifiedPoints.get(simplifiedPoints.size()-1));

            // 2. 获取起点终点坐标
            String originPoint = simplifiedPoints.get(0);
            String destPoint = simplifiedPoints.get(simplifiedPoints.size() - 1);

            // 3. 构造路径参数（高德API格式：线宽,颜色,透明度,,0:路径点）
            String pathValue = "5,0x0000FF,1,,0:" + String.join(",", simplifiedPoints);

            // 4. 构建完整URL
            String staticMapUrl = "https://restapi.amap.com/v3/staticmap?" +
                    "key=" + apiKey +
                    "&size=800*600" +  // 图片尺寸
                    "&paths=" + URLEncoder.encode(pathValue, StandardCharsets.UTF_8) +
                    "&markers=large,0xFF0000,A:" + URLEncoder.encode(originName, StandardCharsets.UTF_8) +
                    "|" + originPoint +
                    "&markers=large,0x00FF00,B:" + URLEncoder.encode(destName, StandardCharsets.UTF_8) +
                    "|" + destPoint;

            // 调试日志（打印部分URL）
            System.out.println("🖼️ 生成静态地图URL: " + staticMapUrl.substring(0, Math.min(staticMapUrl.length(), 150)) + "...");

            // 5. 返回HTML图片标签
            return "<br/><br/><img src=\"" + staticMapUrl +
                    "\" alt=\"从" + originName + "到" + destName + "的路线地图\" " +
                    "style=\"max-width:100%; border:1px solid #ccc; border-radius:8px; box-shadow:0 2px 8px rgba(0,0,0,0.1);\">";
        } catch (Exception e) {
            System.err.println("❌ 生成静态地图URL失败: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    // 简化路径点（避免URL过长）
    private List<String> simplifyPolyline(String polyline, int maxPoints) {
        // 高德地图的polyline格式是：经度,纬度;经度,纬度;...
        String[] points = polyline.split(";");
        List<String> simplified = new ArrayList<>();

        if (points.length <= maxPoints) {
            // 如果点数不多，直接返回所有点
            for (String point : points) {
                simplified.add(point);
            }
            return simplified;
        }

        // 保留起点和终点
        simplified.add(points[0]);
        simplified.add(points[points.length - 1]);

        // 计算步长（均匀采样中间点）
        int step = (points.length - 2) / (maxPoints - 2);
        for (int i = 1; i < points.length - 1; i += step) {
            if (simplified.size() < maxPoints) {
                simplified.add(points[i]);
            }
        }

        return simplified;
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