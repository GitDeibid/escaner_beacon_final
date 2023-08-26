package com.example.apppruebafb

object configInterface {
    private var period:Long = 0
    private var freq:Long=0
    private var nombre_exp:String =""
    private var duracion:Long=0

    fun setConf(p:Long,f:Long,n:String,d:Long){
        period=p
        freq=f
        nombre_exp=n
        duracion=d
    }

    fun getPer():Long{
        return period
    }

    fun getFreq():Long{
        return freq
    }

    fun getNombre():String{
        return nombre_exp
    }

    fun getDur():Long{
        return duracion
    }
}