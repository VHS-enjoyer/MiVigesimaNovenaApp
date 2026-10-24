package com.curso_simulaciones.mivigesimanovenaapp.actividades_secundarias;

import com.curso_simulaciones.mivigesimanovenaapp.datos.AlmacenDatosRAM;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;

public class HiloAnimacion extends Thread {
    public static final long PERIODO_MUESTREO = 100; //ms entre muestras
    public static final int NUM_PUNTOS = 20;         //puntos por barrido
    private static final int ESPERA_MAXIMA = 10;     //muestras sin disparo antes del auto-trigger

    public volatile boolean corriendo = true;        //pausa / reanuda el muestreo
    private volatile boolean activo = true;          //vida del hilo
    private volatile boolean pedirReinicio = false;
    private volatile float nivelTrigger = 0f;
    private volatile boolean flancoSubida = true;

    private final ActividadDesplegadoraDatos actividad;
    private boolean capturando = false;
    private int esperas = 0;
    private float tiempo = 0;
    private float valorAnterior = 0;

    public HiloAnimacion(ActividadDesplegadoraDatos actividad) {
        this.actividad = actividad;
    }

    /* Configura el disparo: nivel y flanco (true = subida, false = bajada) */
    public void setTrigger(float nivel, boolean flancoSubida) {
        this.nivelTrigger = nivel;
        this.flancoSubida = flancoSubida;
    }

    /* Pide empezar de nuevo: se atiende dentro del hilo */
    public void reiniciar() {
        pedirReinicio = true;
    }

    /* Termina el hilo definitivamente */
    public void detener() {
        activo = false;
        interrupt();
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Thread.sleep(PERIODO_MUESTREO);
            } catch (InterruptedException e) {
                break;
            }
            if (pedirReinicio) {
                capturando = false;
                esperas = 0;
                tiempo = 0;
                AlmacenDatosRAM.datos.clear();
                pedirReinicio = false;
            }
            if (!corriendo) continue;

            float v = AlmacenDatosRAM.datoActual;

            //armado: esperar el evento de disparo o el auto-trigger
            if (!capturando) {
                boolean cruce = flancoSubida
                        ? (valorAnterior < nivelTrigger && v >= nivelTrigger)
                        : (valorAnterior > nivelTrigger && v <= nivelTrigger);
                esperas++;
                if (cruce || esperas > ESPERA_MAXIMA) {
                    capturando = true;
                    tiempo = 0;
                    AlmacenDatosRAM.datos.clear(); //se olvida el barrido anterior
                }
            }

            //captura de un barrido completo
            if (capturando) {
                AlmacenDatosRAM.datos.add(new Entry(tiempo, v));
                tiempo = tiempo + 0.001f * PERIODO_MUESTREO; //en segundos
                final ArrayList<Entry> copia = new ArrayList<>(AlmacenDatosRAM.datos);
                actividad.runOnUiThread(() -> actividad.graficador.setDatos(copia));
                if (AlmacenDatosRAM.datos.size() >= NUM_PUNTOS) {
                    capturando = false; //la traza queda congelada hasta el próximo disparo
                    esperas = 0;
                }
            }
            valorAnterior = v;
        }
    }
}