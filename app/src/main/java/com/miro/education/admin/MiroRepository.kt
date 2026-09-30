package com.miro.education.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AdminSession(val userId:String, val email:String, val role:String)
data class ApiResult<T>(val value:T?=null,val error:String?=null)

class SessionStore(context:Context){
    private val p=context.getSharedPreferences("miro_admin_session",Context.MODE_PRIVATE)
    var accessToken:String get()=p.getString("access","")?:""; set(v)=p.edit().putString("access",v).apply()
    var refreshToken:String get()=p.getString("refresh","")?:""; set(v)=p.edit().putString("refresh",v).apply()
    var email:String get()=p.getString("email","")?:""; set(v)=p.edit().putString("email",v).apply()
    var userId:String get()=p.getString("user_id","")?:""; set(v)=p.edit().putString("user_id",v).apply()
    var role:String get()=p.getString("role","")?:""; set(v)=p.edit().putString("role",v).apply()
    var theme:String get()=p.getString("theme","dark")?:"dark"; set(v)=p.edit().putString("theme",v).apply()
    var language:String get()=p.getString("language","ar")?:"ar"; set(v)=p.edit().putString("language",v).apply()
    var lastScreen:String get()=p.getString("screen","dashboard")?:"dashboard"; set(v)=p.edit().putString("screen",v).apply()
    fun clear(){p.edit().clear().apply()}
    val logged:Boolean get()=accessToken.isNotBlank() && userId.isNotBlank()
}

class MiroRepository(private val context:Context, val store:SessionStore){
    private val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.MINUTES).writeTimeout(30,TimeUnit.MINUTES).build()
    private val jsonMedia="application/json; charset=utf-8".toMediaType()
    private val base=BuildConfig.SUPABASE_URL
    private val key=BuildConfig.SUPABASE_PUBLISHABLE_KEY

    private fun req(url:String,method:String="GET",body:String?=null,auth:Boolean=true):Request{
        val b=Request.Builder().url(url).header("apikey",key).header("Accept","application/json")
        if(auth && store.accessToken.isNotBlank()) b.header("Authorization","Bearer ${store.accessToken}")
        val rb=body?.toRequestBody(jsonMedia)
        when(method){
            "POST"->b.post(rb?:ByteArray(0).toRequestBody())
            "PATCH"->b.patch(rb?:ByteArray(0).toRequestBody())
            "DELETE"->b.delete(rb)
            else->b.get()
        }
        return b.build()
    }
    private fun call(rq:Request):ApiResult<String>{
        return try{client.newCall(rq).execute().use{r->
            val raw=r.body?.string().orEmpty()
            if(r.isSuccessful) ApiResult(raw) else ApiResult(error=runCatching{JSONObject(raw).optString("message").ifBlank{JSONObject(raw).optString("error_description")}}.getOrElse{""}.ifBlank{"HTTP ${r.code}"})
        }}catch(e:Exception){ApiResult(error=e.message?:"NETWORK_ERROR")}
    }

    fun login(email:String,password:String):ApiResult<AdminSession>{
        val body=JSONObject().put("email",email.trim()).put("password",password).toString()
        val a=call(req("$base/auth/v1/token?grant_type=password","POST",body,false))
        val o=runCatching{JSONObject(a.value.orEmpty())}.getOrNull()?:return ApiResult(error=a.error?:"LOGIN_FAILED")
        val at=o.optString("access_token"); val rt=o.optString("refresh_token")
        val uid=o.optJSONObject("user")?.optString("id").orEmpty()
        if(at.isBlank()||uid.isBlank())return ApiResult(error="بيانات تسجيل الدخول غير مكتملة")
        store.accessToken=at; store.refreshToken=rt; store.userId=uid; store.email=email.trim()
        val admin=call(req("$base/rest/v1/miro_admins?user_id=eq.$uid&enabled=eq.true&select=role","GET"))
        val arr=runCatching{JSONArray(admin.value.orEmpty())}.getOrNull()
        if(arr==null || arr.length()==0){store.clear();return ApiResult(error="هذا الحساب غير مخوّل كمشرف MIRO")}
        val role=arr.optJSONObject(0)?.optString("role").orEmpty()
        if(role.isBlank()){store.clear();return ApiResult(error="صلاحية المشرف غير صالحة")}
        store.role=role
        return ApiResult(AdminSession(uid,store.email,role))
    }
    fun restore():ApiResult<AdminSession>{
        if(!store.logged)return ApiResult(error="NO_SESSION")
        val me=call(req("$base/auth/v1/user"))
        if(me.value==null){store.clear();return ApiResult(error="انتهت الجلسة")}
        val admin=call(req("$base/rest/v1/miro_admins?user_id=eq.${store.userId}&enabled=eq.true&select=role"))
        val arr=runCatching{JSONArray(admin.value.orEmpty())}.getOrNull()
        if(arr==null||arr.length()==0){store.clear();return ApiResult(error="لم تعد صلاحية المشرف فعّالة")}
        store.role=arr.optJSONObject(0)?.optString("role").orEmpty()
        return ApiResult(AdminSession(store.userId,store.email,store.role))
    }
    fun signOut(){store.clear()}

    fun content():ApiResult<JSONArray>{
        val r=call(req("$base/rest/v1/miro_content?select=*&order=created_at.desc"))
        return ApiResult(r.value?.let{runCatching{JSONArray(it)}.getOrNull()},r.error)
    }
    fun categories():ApiResult<JSONArray>{
        val r=call(req("$base/rest/v1/miro_categories?select=*&order=sort_order.asc"))
        return ApiResult(r.value?.let{runCatching{JSONArray(it)}.getOrNull()},r.error)
    }
    fun admins():ApiResult<JSONArray>{
        val r=call(req("$base/rest/v1/miro_admins?select=user_id,role,enabled"))
        return ApiResult(r.value?.let{runCatching{JSONArray(it)}.getOrNull()},r.error)
    }
    fun count(table:String):Int{
        val r=call(req("$base/rest/v1/$table?select=id&limit=1000"))
        return runCatching{JSONArray(r.value.orEmpty()).length()}.getOrDefault(0)
    }
    fun saveContent(title:String,body:String,kind:String,categoryId:String?,status:String):ApiResult<Boolean>{
        val o=JSONObject().put("kind",kind).put("title",title).put("body",body).put("status",status).put("created_by",store.userId)
        if(!categoryId.isNullOrBlank())o.put("category_id",categoryId)
        val r=call(req("$base/rest/v1/miro_content","POST",o.toString()))
        return if(r.error==null)ApiResult(true) else ApiResult(error=r.error)
    }
    fun updateContent(id:String,title:String,body:String,status:String):ApiResult<Boolean>{
        val o=JSONObject().put("title",title).put("body",body).put("status",status)
        val r=call(req("$base/rest/v1/miro_content?id=eq.$id","PATCH",o.toString()))
        return if(r.error==null)ApiResult(true) else ApiResult(error=r.error)
    }
    fun deleteContent(id:String):ApiResult<Boolean>{
        val r=call(req("$base/rest/v1/miro_content?id=eq.$id","DELETE"))
        return if(r.error==null)ApiResult(true) else ApiResult(error=r.error)
    }
    fun saveCategory(name:String,slug:String):ApiResult<Boolean>{
        val o=JSONObject().put("name",name).put("slug",slug).put("enabled",true)
        val r=call(req("$base/rest/v1/miro_categories","POST",o.toString()))
        return if(r.error==null)ApiResult(true) else ApiResult(error=r.error)
    }
    fun toggleCategory(id:String,enabled:Boolean):ApiResult<Boolean>{
        val o=JSONObject().put("enabled",enabled)
        val r=call(req("$base/rest/v1/miro_categories?id=eq.$id","PATCH",o.toString()))
        return if(r.error==null)ApiResult(true) else ApiResult(error=r.error)
    }

    fun uploadFile(uri:Uri):ApiResult<String>{
        return try{
            val name=queryName(uri) ?: "file_${System.currentTimeMillis()}"
            val safe=name.replace(Regex("[^A-Za-z0-9._-]"),"_")
            val path="admin/${store.userId}/${UUID.randomUUID()}_$safe"
            val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()} ?: return ApiResult(error="تعذر قراءة الملف")
            val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
            val request=Request.Builder().url("$base/storage/v1/object/miro-media/$path")
                .header("apikey",key).header("Authorization","Bearer ${store.accessToken}")
                .header("Content-Type",mime).post(bytes.toRequestBody(mime.toMediaType())).build()
            val r=client.newCall(request).execute()
            val raw=r.body?.string().orEmpty()
            if(r.isSuccessful)ApiResult(path) else ApiResult(error=runCatching{JSONObject(raw).optString("message")}.getOrNull().orEmpty().ifBlank{"رفع الملف فشل HTTP ${r.code}"})
        }catch(e:Exception){ApiResult(error=e.message?:"UPLOAD_FAILED")}
    }
    private fun queryName(uri:Uri):String?{
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
            if(it.moveToFirst())return it.getString(0)
        }
        return uri.lastPathSegment
    }
}