package com.dinehub.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String status;
}
