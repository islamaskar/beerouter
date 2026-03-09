package dev.skynomads.beerouter.mapcreator

import androidx.collection.MutableLongList

public class RelationData(id: Long, public var ways: MutableLongList = MutableLongList(16)) : MapCreatorBase() {
    public var rid: Long = id
    public var description: Long = 0
}
