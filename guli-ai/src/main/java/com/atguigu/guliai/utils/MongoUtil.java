package com.atguigu.guliai.utils;

import com.atguigu.guliai.constant.SystemConstant;

public class MongoUtil {

    /**
     * 根据用户id确定会话集合名称
     * @param projectId
     * @return
     */
    public static String getChatCollectionName(Long projectId){
        return SystemConstant.CHAT_COLLECTION_PREFIX + projectId % SystemConstant.CHAT_COLLECTION_COUNT;
    }

    /**
     * 根据用户id确定会话集合名称
     * @param chatId
     * @return
     */
    public static String getMsgCollectionName(Long chatId){
        return SystemConstant.MSG_COLLECTION_PREFIX+ chatId % SystemConstant.MSG_COLLECTION_COUNT;
    }
}
