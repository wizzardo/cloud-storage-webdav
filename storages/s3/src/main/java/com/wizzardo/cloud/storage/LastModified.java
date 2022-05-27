package com.wizzardo.cloud.storage;

import java.util.Date;

public interface LastModified {
    Date lastModified();

    void lastModified(Date date);
}
