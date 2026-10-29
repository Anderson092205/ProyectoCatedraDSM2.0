import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc } from "firebase/firestore";
let entorno;
const perfil = { nombre: "Estudiante", correo: "prueba@alumno.udb.edu.sv", carnet: "", carrera: "", ciclo: "" };
const materia = { id: "apr", nombre: "Administración de Proyectos", codigo: "APR404", docente: "", correoDocente: "" };
const actividad = { id: "actividad", materiaId: "apr", titulo: "Primer parcial", descripcion: "", tipo: "Parcial", fechaEntrega: 1800000000000, estado: "pendiente", nota: 8, porcentaje: 30 };
const apunte = { id: "apunte", materiaId: "apr", titulo: "Planificación", contenido: "Apuntes del tema", adjuntos: [] };

/** Inicializa únicamente emuladores locales con las reglas que se publican en Firebase. */
before(async () => { entorno = await initializeTestEnvironment({ projectId: "demo-udb-academic", firestore: { rules: await readFile("firestore.rules", "utf8") } }); });
/** Limpia los datos ficticios antes de cada prueba independiente. */
beforeEach(async () => { await entorno.clearFirestore(); });
/** Libera las conexiones de prueba. */
after(async () => { await entorno?.cleanup(); });
/** Crea una sesión ficticia con las credenciales necesarias para las reglas. */
function sesion(id = "uno", correo = "prueba@alumno.udb.edu.sv", verificado = true) { return entorno.authenticatedContext(id, { email: correo, email_verified: verificado }); }
/** Inserta una materia y un apunte ficticios desde el contexto administrador del emulador. */
async function prepararMateria() { await entorno.withSecurityRulesDisabled(async (contexto) => { await setDoc(doc(contexto.firestore(), "estudiantes/uno/materias/apr"), materia); await setDoc(doc(contexto.firestore(), "estudiantes/uno/apuntes/apunte"), apunte); }); }

test("el propietario institucional verificado crea su perfil", async () => { await assertSucceeds(setDoc(doc(sesion().firestore(), "estudiantes/uno"), perfil)); });
test("un visitante sin sesión no lee perfiles", async () => { await assertFails(getDoc(doc(entorno.unauthenticatedContext().firestore(), "estudiantes/uno"))); });
test("el correo personal no accede a datos académicos", async () => { await assertFails(setDoc(doc(sesion("uno", "prueba@gmail.com").firestore(), "estudiantes/uno"), { ...perfil, correo: "prueba@gmail.com" })); });
test("una cuenta sin verificar no accede", async () => { await assertFails(setDoc(doc(sesion("uno", "prueba@alumno.udb.edu.sv", false).firestore(), "estudiantes/uno"), perfil)); });
test("otro estudiante no lee ni modifica los datos privados", async () => { await prepararMateria(); const base = sesion("dos").firestore(); await assertFails(getDoc(doc(base, "estudiantes/uno/materias/apr"))); await assertFails(setDoc(doc(base, "estudiantes/uno/materias/apr"), materia)); });
test("no se puede suplantar el correo del perfil", async () => { await assertFails(setDoc(doc(sesion().firestore(), "estudiantes/uno"), { ...perfil, correo: "otra@alumno.udb.edu.sv" })); });
test("el propietario crea una evaluación válida", async () => { await prepararMateria(); await assertSucceeds(setDoc(doc(sesion().firestore(), "estudiantes/uno/actividades/actividad"), actividad)); });
test("las notas fuera de escala y campos adicionales se rechazan", async () => { await prepararMateria(); const documento = doc(sesion().firestore(), "estudiantes/uno/actividades/actividad"); await assertFails(setDoc(documento, { ...actividad, nota: 11 })); await assertFails(setDoc(documento, { ...actividad, administrador: true })); });
test("una evaluación sin materia existente se rechaza", async () => { await assertFails(setDoc(doc(sesion().firestore(), "estudiantes/uno/actividades/actividad"), actividad)); });
test("las rutas de archivos locales no se guardan en la nube", async () => { await prepararMateria(); await assertFails(setDoc(doc(sesion().firestore(), "estudiantes/uno/apuntes/apunte"), { ...apunte, adjuntos: [{ nombre: "a.pdf", ruta: "estudiantes/dos/apuntes/apunte/archivo", tipo: "application/pdf", tamano: 10 }] })); });
test("los estudiantes no publican noticias", async () => { await assertFails(setDoc(doc(sesion().firestore(), "noticias/nueva"), { titulo: "Publicación no autorizada" })); });

test("el dominio docente de la UDB permite acceso institucional", async () => { await assertSucceeds(setDoc(doc(sesion("uno", "docente@udb.edu.sv").firestore(), "estudiantes/uno"), { ...perfil, correo: "docente@udb.edu.sv" })); });
