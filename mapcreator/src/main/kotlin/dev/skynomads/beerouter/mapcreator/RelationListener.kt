package dev.skynomads.beerouter.mapcreator

public interface RelationListener {
    public fun nextRelation(data: RelationData)

    public fun nextRestriction(data: RelationData, fromWid: Long, toWid: Long, viaNid: Long)
}
