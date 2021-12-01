package org.eclipse.hawkbit.dmf.hono.model;

import lombok.Data;

import java.util.List;

@Data
public class PageList<T> {
    private List<T> content;
}
