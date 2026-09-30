package com.miro.education.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MiroAdminViewModel(val repo:MiroRepository):ViewModel(){
    val store=repo.store
    var logged=false
    var busy=false
    var error:String?=null
    var message:String?=null
    var content=JSONArray()
    var categories=JSONArray()
    var admins=JSONArray()
    var contentCount=0
    var categoryCount=0
    var adminCount=0
    fun boot(onDone:(Boolean)->Unit){
        viewModelScope.launch{
            val r=withContext(Dispatchers.IO){repo.restore()}
            logged=r.value!=null
            onDone(logged)
            if(logged) refresh()
        }
    }
    fun login(email:String,password:String,onDone:(Boolean)->Unit){
        busy=true; error=null
        viewModelScope.launch{
            val r=withContext(Dispatchers.IO){repo.login(email,password)}
            busy=false; logged=r.value!=null; error=r.error
            onDone(logged)
            if(logged)refresh()
        }
    }
    fun logout(){repo.signOut();logged=false}
    fun refresh(){
        viewModelScope.launch{
            busy=true
            val c=withContext(Dispatchers.IO){repo.content()}
            val cats=withContext(Dispatchers.IO){repo.categories()}
            val ad=withContext(Dispatchers.IO){repo.admins()}
            content=c.value?:JSONArray(); categories=cats.value?:JSONArray(); admins=ad.value?:JSONArray()
            contentCount=content.length(); categoryCount=categories.length(); adminCount=admins.length()
            busy=false
            error=c.error?:cats.error?:ad.error
        }
    }
    fun addContent(title:String,body:String,kind:String,categoryId:String?,status:String){
        viewModelScope.launch{
            busy=true
            val r=withContext(Dispatchers.IO){repo.saveContent(title,body,kind,categoryId,status)}
            busy=false; error=r.error; if(r.value==true)refresh()
        }
    }
    fun editContent(id:String,title:String,body:String,status:String){
        viewModelScope.launch{
            busy=true; val r=withContext(Dispatchers.IO){repo.updateContent(id,title,body,status)}
            busy=false; error=r.error; if(r.value==true)refresh()
        }
    }
    fun deleteContent(id:String){
        viewModelScope.launch{
            busy=true; val r=withContext(Dispatchers.IO){repo.deleteContent(id)}
            busy=false; error=r.error; if(r.value==true)refresh()
        }
    }
    fun addCategory(name:String,slug:String){
        viewModelScope.launch{
            busy=true; val r=withContext(Dispatchers.IO){repo.saveCategory(name,slug)}
            busy=false; error=r.error; if(r.value==true)refresh()
        }
    }
    fun toggleCategory(id:String,enabled:Boolean){
        viewModelScope.launch{
            val r=withContext(Dispatchers.IO){repo.toggleCategory(id,enabled)}
            error=r.error; if(r.value==true)refresh()
        }
    }
    fun upload(uri:Uri,onDone:(String?)->Unit){
        viewModelScope.launch{
            busy=true
            val r=withContext(Dispatchers.IO){repo.uploadFile(uri)}
            busy=false; error=r.error; onDone(r.value)
        }
    }
}