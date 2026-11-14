package com.joyous.remap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Acción propia de un perfil para una pulsación (si falta, hereda la general). */
data class ProfileAct(val act: Act, val pkg: String? = null)

/**
 * Perfil por aplicación. El orden en la lista es la prioridad: gana el primero activo que encaje.
 * [except] = false: solo en [pkgs]; true: en todas menos en [pkgs].
 */
data class Profile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val except: Boolean = false,
    val pkgs: List<String> = emptyList(),
    val acts: Map<Gesture, ProfileAct> = emptyMap(),
    /** Accesos propios del menú rápido; null = usa los generales. */
    val quick: List<ProfileAct>? = null
) {
    fun appliesTo(pkg: String): Boolean = if (except) pkg !in pkgs else pkg in pkgs
}

/** Resultado de mirar la app en primer plano: excluida, con perfil o sin nada (ajustes generales). */
class Eff(val excluded: Boolean, val profile: Profile?) {
    fun act(c: Context, g: Gesture): Act = profile?.acts?.get(g)?.act ?: Prefs.action(c, g)

    fun pkg(c: Context, g: Gesture): String? {
        val pa = profile?.acts?.get(g)
        return if (pa != null) pa.pkg else Prefs.pkg(c, g)
    }

    /** Accesos del menú rápido: los del perfil si los tiene, si no los generales. */
    fun quick(c: Context): List<ProfileAct> = profile?.quick?.takeIf { it.size >= 2 } ?: Prefs.quickItems(c)

    companion object {
        val NONE = Eff(false, null)
    }
}

object Profiles {
    private const val K_PROFILES = "profiles_json"
    private const val K_EXCLUDED = "excluded_apps"

    private var rawP: String? = null
    private var cacheP: List<Profile> = emptyList()
    private var rawX: String? = null
    private var cacheX: Set<String> = emptySet()

    /** Se guarda en las mismas preferencias que el resto, así entra en exportar / importar. */
    @Synchronized
    fun all(c: Context): List<Profile> {
        val raw = Prefs.sp(c).getString(K_PROFILES, null) ?: return emptyList()
        if (raw == rawP) return cacheP
        cacheP = try {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
        rawP = raw
        return cacheP
    }

    @Synchronized
    fun save(c: Context, list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        val raw = a.toString()
        Prefs.sp(c).edit().putString(K_PROFILES, raw).apply()
        rawP = raw
        cacheP = list
    }

    fun get(c: Context, id: String): Profile? = all(c).firstOrNull { it.id == id }

    fun update(c: Context, id: String, f: (Profile) -> Profile) {
        save(c, all(c).map { if (it.id == id) f(it) else it })
    }

    fun add(c: Context, p: Profile) = save(c, all(c) + p)

    fun delete(c: Context, id: String) = save(c, all(c).filter { it.id != id })

    /** Mueve un perfil en la lista: [dir] = -1 sube (más prioridad), 1 baja. */
    fun move(c: Context, id: String, dir: Int) {
        val l = all(c).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        save(c, l)
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (0..99).random()

    // ----- Aplicaciones excluidas por completo -----

    @Synchronized
    fun excluded(c: Context): Set<String> {
        val raw = Prefs.sp(c).getString(K_EXCLUDED, null) ?: return emptySet()
        if (raw == rawX) return cacheX
        cacheX = try {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        rawX = raw
        return cacheX
    }

    @Synchronized
    fun setExcluded(c: Context, s: Set<String>) {
        val raw = JSONArray(s.toList()).toString()
        Prefs.sp(c).edit().putString(K_EXCLUDED, raw).apply()
        rawX = raw
        cacheX = s
    }

    // ----- Resolución -----

    /** ¿Hay algo configurado? Si no, no se consulta la app en primer plano. */
    fun any(c: Context): Boolean = all(c).isNotEmpty() || excluded(c).isNotEmpty()

    /** Exclusión > primer perfil activo que encaje (prioridad por orden) > ajustes generales. */
    fun resolve(c: Context, pkg: String?): Eff {
        if (pkg == null || pkg == c.packageName) return Eff.NONE
        if (pkg in excluded(c)) return Eff(true, null)
        val p = all(c).firstOrNull { it.enabled && it.appliesTo(pkg) }
        return if (p == null) Eff.NONE else Eff(false, p)
    }

    // ----- JSON -----

    fun encodeItems(l: List<ProfileAct>): JSONArray {
        val a = JSONArray()
        l.forEach { pa ->
            val x = JSONObject().put("a", pa.act.id)
            if (pa.pkg != null) x.put("p", pa.pkg)
            a.put(x)
        }
        return a
    }

    fun decodeItems(a: JSONArray?): List<ProfileAct>? {
        if (a == null) return null
        return (0 until a.length()).mapNotNull {
            val x = a.optJSONObject(it) ?: return@mapNotNull null
            val act = Act.from(x.optString("a", "none"))
            if (act == Act.NONE || act == Act.QUICK) null
            else ProfileAct(act, x.optString("p", "").ifEmpty { null })
        }
    }

    private fun toJson(p: Profile): JSONObject {
        val o = JSONObject()
        o.put("id", p.id).put("name", p.name).put("on", p.enabled).put("except", p.except)
        o.put("pkgs", JSONArray(p.pkgs))
        val a = JSONObject()
        for ((g, pa) in p.acts) {
            val x = JSONObject().put("a", pa.act.id)
            if (pa.pkg != null) x.put("p", pa.pkg)
            a.put(g.key, x)
        }
        o.put("acts", a)
        p.quick?.let { o.put("quick", encodeItems(it)) }
        return o
    }

    private fun parse(o: JSONObject): Profile? {
        val id = o.optString("id", "")
        if (id.isEmpty()) return null
        val pk = o.optJSONArray("pkgs")
        val pkgs = if (pk == null) emptyList() else (0 until pk.length()).map { pk.getString(it) }
        val acts = mutableMapOf<Gesture, ProfileAct>()
        val a = o.optJSONObject("acts")
        if (a != null) for (g in Gesture.values()) {
            val x = a.optJSONObject(g.key) ?: continue
            acts[g] = ProfileAct(Act.from(x.optString("a", "none")), x.optString("p", "").ifEmpty { null })
        }
        return Profile(id, o.optString("name", ""), o.optBoolean("on", true), o.optBoolean("except", false), pkgs, acts, decodeItems(o.optJSONArray("quick")))
    }
}
