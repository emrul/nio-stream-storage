package com.synchronoss.nio.file.multipart;

import com.synchronoss.nio.file.multipart.BodyStreamFactory.PartOutputStream;
import org.apache.commons.fileupload.ParameterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * <p>
 * NIO Multipart Parser that process data streams conforming to MIME 'multipart' format as defined in
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>.
 * </p>
 *
 * <p> The format of the stream is defined in the following way:<br>
 *
 * <code>
 *   multipart-body := preamble 1*encapsulation close-delimiter epilogue<br>
 *   encapsulation := delimiter body CRLF<br>
 *   delimiter := "--" boundary CRLF<br>
 *   close-delimiter := "--" boundary "--"<br>
 *   preamble := &lt;ignore&gt;<br>
 *   epilogue := &lt;ignore&gt;<br>
 *   body := header-part CRLF body-part<br>
 *   header-part := 1*header CRLF<br>
 *   header := header-name ":" header-value<br>
 *   header-name := &lt;printable ascii characters except ":"&gt;<br>
 *   header-value := &lt;any ascii characters except CR & LF&gt;<br>
 *   body-data := &lt;arbitrary data&gt;<br>
 * </code>
 *
 * Have a look at:
 * {@link org.glassfish.grizzly.http.multipart.MultipartScanner}
 * {@link org.glassfish.grizzly.http.multipart.MultipartReadHandler}
 * {@link org.apache.commons.fileupload.MultipartStream}
 *
 * Created by sriz0001 on 15/10/2015.
 */
public class NioMultipartParserImpl implements NioMultipartParser, Closeable {

    private static final Logger log = LoggerFactory.getLogger(NioMultipartParserImpl.class);

    public static final byte DASH = 0x2D;
    public static final byte CR = 0x0D;
    public static final byte LF = 0x0A;

    public static final int DEFAULT_BUFFER_SIZE = 16000;//16kb, Enough for separator and a full header line.
    public static final String CLOSE_DELIMITER_NAME = "Close-Delimiter";
    public static final String DELIMITER_NAME = "Delimiter";
    public static final String HEADER_DELIMITER_NAME = "Header-Delimiter";
    public static final byte[] HEADER_DELIMITER = {CR, LF};

    protected enum State {
        SKIP_PREAMBLE, GET_READY_FOR_HEADERS, HEADERS, GET_READY_FOR_BODY, BODY, END
    }

    final MultipartContext multipartContext;
    final NioMultipartParserListener nioMultipartParserListener;
    final BodyStreamFactory bodyStreamFactory;
    final EndOfLineBuffer buffer;
    final Map<String, byte[]> preambleDelimiters;
    final Map<String, byte[]> delimiters;
    final Map<String, byte[]> headersDelimiter;

    // Current state of the ASF
    State currentState = State.SKIP_PREAMBLE;

    // Current output stream where to flush the body data.
    // It will be instantiated for each part via {@link BodyStreamFactory#getOutputStream(Map, int)} )}
    OutputStream outputStream = null;

    // Stream where to flush the header data. will be reset every time an header is parsed.
    ByteArrayOutputStream headerOutputStream = new ByteArrayOutputStream();

    // The current headers.
    Map<String, List<String>> headers = null;

    // ------------
    // Constructors
    // ------------
    public NioMultipartParserImpl(final MultipartContext multipartContext, final NioMultipartParserListener nioMultipartParserListener) {
        this(multipartContext, nioMultipartParserListener, null, DEFAULT_BUFFER_SIZE);
    }

    public NioMultipartParserImpl(final MultipartContext multipartContext, final NioMultipartParserListener nioMultipartParserListener, final BodyStreamFactory bodyStreamFactory) {
        this(multipartContext, nioMultipartParserListener, bodyStreamFactory, DEFAULT_BUFFER_SIZE);
    }

    public NioMultipartParserImpl(final MultipartContext multipartContext, final NioMultipartParserListener nioMultipartParserListener, final int bufferSize) {
        this(multipartContext, nioMultipartParserListener, null, bufferSize);
    }

    public NioMultipartParserImpl(final MultipartContext multipartContext, final NioMultipartParserListener nioMultipartParserListener, final BodyStreamFactory bodyStreamFactory, final int bufferSize) {
        this.multipartContext = multipartContext;
        this.nioMultipartParserListener = nioMultipartParserListener;

        this.preambleDelimiters = getPreambleDelimiters(multipartContext.getContentType());
        this.delimiters = getMultipartDelimiters(multipartContext.getContentType());
        this.headersDelimiter = new HashMap<String, byte[]>(1);
        this.headersDelimiter.put(HEADER_DELIMITER_NAME, HEADER_DELIMITER);

        if (bodyStreamFactory != null){
            this.bodyStreamFactory = bodyStreamFactory;
        }else{
            // By default use a temporary file where to save the body data.
            this.bodyStreamFactory = new TempFileBodyStreamFactory();
        }

        // At the beginning set up the buffer to skip the preamble.
        this.buffer = new EndOfLineBuffer(bufferSize, this.preambleDelimiters, null);

        debug = this.bodyStreamFactory.getOutputStream(new HashMap<String, List<String>>(),0);
    }

    @Override
    public void close() throws IOException {
        // TODO - DO I need to release some resources?
    }

    @Override
    public void handleBytesReceived(byte[] receivedBytes, int indexStart, int indexEnd) {

        writeForDebug(receivedBytes, indexStart, indexEnd);

        // This method implements a state machine. At each written byte the state can change if a end of line is found.

        int partIndex = 0;
        int currentIndex = indexStart;

        while (currentIndex < indexEnd) {
            switch (currentState) {

                case SKIP_PREAMBLE:
                    if (log.isDebugEnabled())log.info("Skip preamble");
                    currentIndex = readPreambleByte(receivedBytes, currentIndex, indexEnd);
                    break;

                case GET_READY_FOR_HEADERS:
                    if (log.isDebugEnabled())log.info("Get ready for headers");
                    getReadyForHeaders();
                    break;

                case HEADERS:
                    if (log.isDebugEnabled())log.info("Parse headers");
                    currentIndex = readHeadersByte(receivedBytes, currentIndex, indexEnd);
                    break;

                case GET_READY_FOR_BODY:
                    if (log.isDebugEnabled())log.info("Get ready for body");
                    getReadyForBody(partIndex++);
                    break;

                case BODY:
                    if (log.isDebugEnabled())log.info("Read body");
                    currentIndex = readBodyByte(receivedBytes, currentIndex, indexEnd);
                    break;

                case END:
                    if (log.isDebugEnabled())log.info("End");
                    nioMultipartParserListener.onAllPartsRead();
                    currentIndex = indexEnd + 1; // To Exit the while...
                    logDebugFile();
                    break;
            }
        }
    }

    int readPreambleByte(final byte[] receivedBytes, int currentIndex, final int indexEnd){
        for (; currentIndex < indexEnd; currentIndex++) {
            if (buffer.write(receivedBytes[currentIndex])) {

                if (DELIMITER_NAME.equals(buffer.getEndOfLineName())) {
                    currentState = State.GET_READY_FOR_HEADERS;
                    return ++currentIndex;

                } else if(CLOSE_DELIMITER_NAME.equals(buffer.getEndOfLineName())) {
                    currentState = State.END;
                    return ++currentIndex;

                } else {
                    throw new IllegalStateException("Expected a delimiter after the preamble.");
                }
            }
        }
        return ++currentIndex;
    }

    void getReadyForHeaders(){
        currentState = State.HEADERS;
        headerOutputStream.reset();
        buffer.reset(headersDelimiter, headerOutputStream);
        headers = new HashMap<String, List<String>>();
    }

    int readHeadersByte(final byte[] receivedBytes, int currentIndex, final int indexEnd){
        for (; currentIndex < indexEnd; currentIndex++) {
            if (buffer.write(receivedBytes[currentIndex])) {
                ByteArrayOutputStream headersOutputStream = (ByteArrayOutputStream)outputStream;
                String header = new String(headersOutputStream.toByteArray());// TODO - encoding
                if (header.trim().length() == 0){
                    // Got an empty value, it means the header section is finished.
                    currentState = State.GET_READY_FOR_BODY;

                }else{
                    // Parse header
                    String[] headerComponents = header.split(":");
                    List<String> headerValues = new ArrayList<String>();
                    if (headerComponents.length > 1){
                        Collections.addAll(headerValues, headerComponents[1].split(","));
                    }
                    headers.put(headerComponents[0], headerValues);
                    headersOutputStream.reset();
                    buffer.reset(headersDelimiter, this.outputStream);
                }
                return ++currentIndex;
            }
        }
        return ++currentIndex;
    }

    void getReadyForBody(final int partIndex){
        currentState = State.BODY;
        outputStream = bodyStreamFactory.getOutputStream(headers, partIndex);
        buffer.reset(delimiters, outputStream);
    }

    int readBodyByte(final byte[] receivedBytes, int currentIndex, final int indexEnd){
        for (; currentIndex < indexEnd; currentIndex++) {
            if (buffer.write(receivedBytes[currentIndex])) {
                if (DELIMITER_NAME.equals(buffer.getEndOfLineName())){
                    currentState = State.GET_READY_FOR_HEADERS;
                }else{
                    currentState = State.END;
                }
                final String name = ((PartOutputStream)outputStream).getName();
                final InputStream partBodyInputStream =  bodyStreamFactory.getInputStream(name);
                nioMultipartParserListener.onPartComplete(partBodyInputStream, headers);
                return ++currentIndex;
            }
        }
        return ++currentIndex;
    }

    // TODO - get rid of the commons fileupload dependency
    byte[] getBoundary(final String contentType) {
        ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        Map<String, String> params = parser.parse(contentType, new char[] {';', ','});
        String boundaryStr = params.get("boundary");

        if (boundaryStr == null) {
            return null;
        }
        byte[] boundary;
        try {
            boundary = boundaryStr.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            boundary = boundaryStr.getBytes(); // Intentionally falls back to default charset
        }
        return boundary;
    }

    Map<String, byte[]> getPreambleDelimiters(final String contentType){
        byte[] boundary = getBoundary(contentType);

        byte[] closeDelimiter = new byte[boundary.length + 4];
        closeDelimiter[0] = DASH;
        closeDelimiter[1] = DASH;
        closeDelimiter[closeDelimiter.length - 1] = DASH;
        closeDelimiter[closeDelimiter.length - 2] = DASH;
        System.arraycopy(boundary, 0, closeDelimiter, 2, boundary.length);

        byte[] delimiter = new byte[boundary.length + 4];
        delimiter[0] = DASH;
        delimiter[1] = DASH;
        delimiter[delimiter.length - 2] = CR;
        delimiter[delimiter.length - 1] = LF;
        System.arraycopy(boundary, 0, delimiter, 2, boundary.length);

        Map<String, byte[]> delimiters = new HashMap<String, byte[]>(2);
        delimiters.put(CLOSE_DELIMITER_NAME, closeDelimiter);
        delimiters.put(DELIMITER_NAME, delimiter);

        return delimiters;

    }

    Map<String, byte[]> getMultipartDelimiters(final String contentType){
        byte[] boundary = getBoundary(contentType);

        byte[] closeDelimiter = new byte[boundary.length + 6];
        closeDelimiter[0] = CR;
        closeDelimiter[1] = LF;
        closeDelimiter[2] = DASH;
        closeDelimiter[3] = DASH;
        closeDelimiter[closeDelimiter.length - 1] = DASH;
        closeDelimiter[closeDelimiter.length - 2] = DASH;
        System.arraycopy(boundary, 0, closeDelimiter, 4, boundary.length);

        byte[] delimiter = new byte[boundary.length + 6];
        delimiter[0] = CR;
        delimiter[1] = LF;
        delimiter[2] = DASH;
        delimiter[3] = DASH;
        delimiter[delimiter.length - 2] = CR;
        delimiter[delimiter.length - 1] = LF;
        System.arraycopy(boundary, 0, delimiter, 4, boundary.length);

        Map<String, byte[]> delimiters = new HashMap<String, byte[]>(2);
        delimiters.put(CLOSE_DELIMITER_NAME, closeDelimiter);
        delimiters.put(DELIMITER_NAME, delimiter);

        return delimiters;

    }


    // ================
    // Just for testing
    // ================
    private static final boolean DEBUG = true;
    PartOutputStream debug;
    void writeForDebug(final byte[] receivedBytes, final int indexStart, final int indexEnd){
        if (DEBUG){
            try {
                debug.write(receivedBytes, indexStart, indexEnd);
            }catch (Exception e){
                // nothing to do
            }
        }
    }

    void logDebugFile(){
        if (DEBUG){
            log.debug("Multipart request body is stored here: " + debug.getName());
        }
    }

}
