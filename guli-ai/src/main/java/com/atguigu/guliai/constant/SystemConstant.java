package com.atguigu.guliai.constant;

public class SystemConstant {
    public static final String VECTOR_STORE_OPENAI = "openai.vector.store";
    public static final String VECTOR_STORE_OLLAMA = "ollama.vector.store";

    public static final String MODEL_TYPE_OPENAI = "openai";
    public static final String MODEL_TYPE_OLLAMA = "ollama";

    public static final String CHAT_COLLECTION_PREFIX = "chat_";
    public static final String MSG_COLLECTION_PREFIX = "message_";

    public static final int CHAT_COLLECTION_COUNT = 100;
    public static final int MSG_COLLECTION_COUNT = 1000;

    public static final int TOP_K = 3;
}
