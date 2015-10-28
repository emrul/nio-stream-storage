/*
 * Copyright (C) 2015 Synchronoss Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.synchronoss.cloud.nio.multipart;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <p>
 *     Unit tests for {@link NioMultipartParser}
 * </p>
 * @author Silvano Riz.
 */
public class NioMultipartParserTest {

    private static final Logger log = LoggerFactory.getLogger(NioMultipartParserTest.class);

    @Test
    public void testConstruction(){

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);
        PartStreamsFactory partStreamsFactory = mock(PartStreamsFactory.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);
        assertNotNull(parser);

        NioMultipartParser parser1 = new NioMultipartParser(context, listener, 5000);
        assertNotNull(parser1);

        NioMultipartParser parser2 = new NioMultipartParser(context, listener, partStreamsFactory);
        assertNotNull(parser2);

        NioMultipartParser parser3 = new NioMultipartParser(context, listener, partStreamsFactory, 5000, 5000, 1);
        assertNotNull(parser3);

    }

    @Test
    public void testWrite_emptyData() throws IOException {

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);
        parser.write(new byte[]{});

    }

    @Test
    public void testWrite_error1(){

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);

        Exception expected = null;
        try {
            parser.write(null, 0, 10);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

        expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 0, 2);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

    }


    @Test
    public void testWrite_error2(){

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);

        Exception expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 9, 10);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

        expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 0, 2);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

    }

    @Test
    public void testWrite_error3(){

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);

        Exception expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 3, 2);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

        expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 0, 2);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

    }

    @Test
    public void testWrite_error4(){

        MultipartContext context = mock(MultipartContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data;boundary=MUEYT2qJT0_ZzYUvVQLy_DlrLeADyxzmsA");

        NioMultipartParserListener listener = mock(NioMultipartParserListener.class);

        NioMultipartParser parser = new NioMultipartParser(context, listener);

        Exception expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 0, 15);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

        expected = null;
        try {
            parser.write(new byte[]{0x01, 0x02, 0x03}, 0, 2);
        }catch (Exception e){
            expected = e;
        }
        Assert.assertNotNull(expected);

    }

}