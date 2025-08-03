package com.poppin.poppinserver.popup.domain;

import com.poppin.poppinserver.core.type.EOperationStatus;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;

import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "popup-store")
public class PopupSearchDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String name;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String introduce;

    @Field(type = FieldType.Text, analyzer = "nori")
    private String address;

    @Field(type = FieldType.Keyword)
    private EOperationStatus operationStatus;

    @Field(type = FieldType.Keyword)
    private List<String> prefered; // 예: "fashionBeauty", "kpop" 등

    @Field(type = FieldType.Keyword)
    private List<String> taste;  // 예: "market", "display" 등

    @Field(type = FieldType.Date)
    private LocalDate openDate;

    @Field(type = FieldType.Date)
    private LocalDate closeDate;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime editedAt;

    @Field(type = FieldType.Integer)
    private int viewCnt;
}
