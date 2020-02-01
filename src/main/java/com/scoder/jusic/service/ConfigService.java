package com.scoder.jusic.service;

/**
 * @author H
 */
public interface ConfigService {

    /**
     * set push switch
     *
     * @param pushSwitch boolean
     */
    void setPushSwitch(boolean pushSwitch);

    /**
     * get by key
     *
     * @param key key
     * @return the key's value
     */
    Object get(String key);
}
