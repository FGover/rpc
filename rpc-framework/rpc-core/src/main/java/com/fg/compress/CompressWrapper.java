package com.fg.compress;

import com.fg.compress.service.Compressor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompressWrapper {

    private byte code;
    private String name;
    private Compressor compressor;
}
