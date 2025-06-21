package com.fg.serialize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SerializerWrapper<T> {

    private byte code;
    private String name;
    private T serializer;
}
