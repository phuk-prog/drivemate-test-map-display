package com.drivemate.app.nav

import kotlin.math.cos
import kotlin.math.sin

/** Shared inexpensive Canvas styling. Changes presentation, never topology or lane selection. */
internal object ScenePresentation {
    fun blend(a: Long, b: Long, fraction: Float): Long {
        val t=fraction.coerceIn(0f,1f)
        fun channel(shift:Int)=(((a shr shift) and 255)*(1-t)+((b shr shift) and 255)*t).toLong()
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun rect(out: MutableList<JunctionScene.Shape>, x:Float,y:Float,w:Float,h:Float,color:Long) {
        out += JunctionScene.Shape.Fill(listOf(JunctionScene.P(x,y),JunctionScene.P(x+w,y),
            JunctionScene.P(x+w,y+h),JunctionScene.P(x,y+h)),color)
    }

    fun gradient(out: MutableList<JunctionScene.Shape>, x:Float,y:Float,w:Float,h:Float,top:Long,bottom:Long) {
        for(i in 0 until 32) rect(out,x,y+h*i/32,w,h/32+.001f,blend(top,bottom,i/31f))
    }

    fun rounded(out: MutableList<JunctionScene.Shape>,x:Float,y:Float,w:Float,h:Float,radius:Float,color:Long) {
        val r=radius.coerceIn(0f,minOf(w,h)/2)
        val points=mutableListOf<JunctionScene.P>()
        for((cx,cy,start) in listOf(Triple(x+w-r,y+r,-90),Triple(x+w-r,y+h-r,0),
            Triple(x+r,y+h-r,90),Triple(x+r,y+r,180))) {
            for(i in 0..8) {
                val angle=Math.toRadians((start+i*90.0/8))
                points += JunctionScene.P(cx+r*cos(angle).toFloat(),cy+r*sin(angle).toFloat())
            }
        }
        out += JunctionScene.Shape.Fill(points,color)
    }

    fun disc(out:MutableList<JunctionScene.Shape>,x:Float,y:Float,r:Float,color:Long) {
        out += JunctionScene.Shape.Fill((0..48).map {
            val angle=it*Math.PI/24
            JunctionScene.P(x+r*cos(angle).toFloat(),y+r*sin(angle).toFloat())
        },color)
    }

    /** True viewport clipping, rather than flattening projected near vertices against a panel edge. */
    fun clip(points:List<JunctionScene.P>,left:Float,top:Float,right:Float,bottom:Float):List<JunctionScene.P> {
        var result=points
        for(edge in 0..3) {
            val input=result
            if(input.isEmpty())break
            val clipped=mutableListOf<JunctionScene.P>()
            fun axis(p:JunctionScene.P)=if(edge<2)p.x else p.y
            val limit=when(edge){0->left;1->right;2->top;else->bottom}
            fun inside(p:JunctionScene.P)=if(edge==0 || edge==2)axis(p)>=limit else axis(p)<=limit
            var previous=input.last()
            for(p in input) {
                if(inside(previous)!=inside(p)) {
                    val t=(limit-axis(previous))/(axis(p)-axis(previous))
                    clipped += JunctionScene.P(previous.x+(p.x-previous.x)*t,previous.y+(p.y-previous.y)*t)
                }
                if(inside(p))clipped+=p
                previous=p
            }
            result=clipped
        }
        return result
    }

    fun label(out:MutableList<JunctionScene.Shape>,text:String,x:Float,y:Float,height:Float,
              maxWidth:Float,color:Long,background:Long) {
        val requested=height/7
        val textWidth=ReplayLettering.measure(text,requested)
        val scale=if(textWidth>maxWidth) requested*maxWidth/textWidth else requested
        ReplayLettering.draw(out,text,x,y,scale,color,background)
    }
}
