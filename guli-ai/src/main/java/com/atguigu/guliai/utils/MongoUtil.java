package com.atguigu.guliai.utils;

import com.atguigu.guliai.constant.SystemConstant;

public class MongoUtil {

    /**
     * 根据用户id确定会话集合名称
     * @param userId
     * @return
     */
    public static String getChatCollectionName(Long userId){
        return SystemConstant.CHAT_COLLECTION_PREFIX + userId % SystemConstant.CHAT_COLLECTION_COUNT;
    }

    /**
     * 根据用户id确定会话集合名称
     * @param userId
     * @return
     */
    public static String getMsgCollectionName(Long userId){
        return SystemConstant.MSG_COLLECTION_PREFIX+ userId % SystemConstant.MSG_COLLECTION_COUNT;
    }
}
