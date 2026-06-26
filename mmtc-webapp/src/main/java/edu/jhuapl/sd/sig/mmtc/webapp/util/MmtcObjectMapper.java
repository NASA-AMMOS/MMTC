package edu.jhuapl.sd.sig.mmtc.webapp.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import edu.jhuapl.sd.sig.mmtc.products.model.kernel.sclk.CorrelationTriplet;
import edu.jhuapl.sd.sig.mmtc.util.Settable;
import edu.jhuapl.sd.sig.mmtc.util.TimeConvert;

import java.io.IOException;
import java.time.OffsetDateTime;

public class MmtcObjectMapper {
    public static ObjectMapper get() {
        final ObjectMapper objectMapper = new ObjectMapper();

        final SimpleModule mmtcCustomMapper = new SimpleModule();

        mmtcCustomMapper.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                jsonGenerator.writeString(TimeConvert.timeToIsoUtcString(offsetDateTime, 9));
            }
        });

        mmtcCustomMapper.addSerializer(Settable.class, new JsonSerializer<>() {
            @Override
            public void serialize(Settable settable, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                if (settable.isSet()) {
                    serializerProvider.defaultSerializeValue(settable.get(), jsonGenerator);
                } else {
                    jsonGenerator.writeNull();
                }
            }
        });

        mmtcCustomMapper.addDeserializer(OffsetDateTime.class, new JsonDeserializer<>() {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
                return TimeConvert.parseIsoDoyUtcStr(jsonParser.getText());
            }
        });

        objectMapper.registerModule(mmtcCustomMapper);

        objectMapper.addMixIn(
                CorrelationTriplet.class,
                CorrelationTripletMixIn.class
        );

        return objectMapper;
    }
}
