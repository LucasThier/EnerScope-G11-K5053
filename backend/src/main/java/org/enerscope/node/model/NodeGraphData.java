package org.enerscope.node.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NodeGraphData extends BaseEntity {

    @Column(name = "xPosition")
    private Double xPosition;

    @Column(name = "yPosition")
    private Double yPosition;

    @Column(name = "coordinates")
    private Double coordinates;
}