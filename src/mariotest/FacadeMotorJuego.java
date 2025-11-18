package mariotest;

import SistemaDeSoporte.EstadoJuego;
import Entidades.Jugador;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;

/**
 * Patrón FACADE - Game Loop refactorizado
 * 
 * CAMBIOS:
 * - Orden de renderizado clarificado
 * - Lógica de victoria simplificada
 * - Separación de responsabilidades mejorada
 */
public class FacadeMotorJuego implements Runnable {
    
    private static final int NANOS_PER_SEC = 1000000000;
    private static final double TARGET_TPS = 60.0;
    private static final int MILLIS_PER_SEC = 1000;
    
    private final ContextoJuego contexto;
    private final Canvas canvas;
    private final Thread thread;
    
    private volatile boolean running;
    private boolean debug;
    
    private int fps;
    private int tps;
    
    public FacadeMotorJuego(ContextoJuego contexto, Canvas canvas) {
        this.contexto = contexto;
        this.canvas = canvas;
        this.thread = new Thread(this, "GameLoop-Thread");
        this.running = false;
        this.debug = false;
        this.fps = 0;
        this.tps = 0;
    }
    
    public synchronized void iniciar() {
        if (running) {
            System.err.println("[LOOP] Ya está corriendo");
            return;
        }
        
        System.out.println("[LOOP] Iniciando game loop...");
        running = true;
        thread.start();
    }
    
    public synchronized void detener() {
        if (!running) {
            return;
        }
        
        System.out.println("[LOOP] Deteniendo game loop...");
        running = false;
        
        try {
            thread.join();
            System.out.println("[LOOP] ✓ Game loop detenido correctamente");
        } catch (InterruptedException e) {
            System.err.println("[LOOP] Error deteniendo: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double ns = NANOS_PER_SEC / TARGET_TPS;
        double delta = 0;
        long timer = System.currentTimeMillis();
        int frames = 0;
        int updates = 0;
        
        canvas.requestFocus();
        
        System.out.println("[LOOP] ✓ Game loop activo (60 TPS)");
        
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            
            while (delta >= 1) {
                tick();
                updates++;
                delta--;
            }
            
            if (running) {
                render();
                frames++;
            }
            
            if (System.currentTimeMillis() - timer > MILLIS_PER_SEC) {
                timer += MILLIS_PER_SEC;
                fps = frames;
                tps = updates;
                
                if (debug) {
                    imprimirStats();
                }
                
                frames = 0;
                updates = 0;
            }
        }
    }
    
    /**
     * Actualiza la lógica del juego
     * Patrón: CHAIN OF RESPONSIBILITY (orden de actualización)
     */
    private void tick() {
        // 1. Actualizar gestor de estados (menús, pausas)
        if (contexto.getGestorEstados() != null) {
            contexto.getGestorEstados().tick();
        }
        
        // 2. Solo actualizar juego si estamos en estado JUGANDO
        if (contexto.getGestorEstados() != null && contexto.getGestorEstados().estaJugando()) {
            tickJuego();
        }
        
        // 3. Verificar condiciones críticas
        verificarMuerte();
    }
    
    /**
     * Actualiza la lógica del juego (solo cuando está jugando)
     */
    private void tickJuego() {
        // 1. Actualizar gestor de niveles
        if (contexto.getGestorNiveles() != null) {
            contexto.getGestorNiveles().tick();
        }
        
        // 2. Actualizar objetos si el nivel lo permite
        boolean permitirJuego = contexto.getGestorNiveles() == null 
            || contexto.getGestorNiveles().permitirMovimientoJugador();
        
        if (permitirJuego && contexto.getHandler() != null) {
            contexto.getHandler().tick();
        }
    }
    
    /**
     * Renderiza el juego
     * Patrón: COMPOSITE (capas de renderizado)
     * 
     * ORDEN CRÍTICO:
     * 1. Objetos del juego (Handler)
     * 2. Overlay de nivel (transiciones, victoria de nivel)
     * 3. UI Global (menús, pantalla de victoria FINAL)
     */
    private void render() {
        BufferStrategy buffer = canvas.getBufferStrategy();
        
        if (buffer == null) {
            canvas.createBufferStrategy(3);
            return;
        }
        
        Graphics g = buffer.getDrawGraphics();
        
        try {
            // Limpiar pantalla
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            
            GestorEstados gestor = contexto.getGestorEstados();
            
            // ==================== CAPA 1: JUEGO (solo si está jugando) ====================
            if (gestor != null && gestor.estaJugando()) {
                // Renderizar objetos del juego
                if (contexto.getHandler() != null) {
                    contexto.getHandler().render(g);
                }
                
                // Renderizar overlay de nivel (transiciones entre niveles)
                if (contexto.getGestorNiveles() != null) {
                    contexto.getGestorNiveles().render(g);
                }
            }
            
            // ==================== CAPA 2: UI GLOBAL (siempre encima) ====================
            // Esta capa incluye menús y la pantalla de victoria FINAL
            if (gestor != null) {
                gestor.render(g);
            }
            
            // ==================== CAPA 3: DEBUG ====================
            if (debug) {
                renderDebug(g);
            }
            
        } finally {
            g.dispose();
        }
        
        buffer.show();
    }
    
    /**
     * Verifica condiciones de muerte del jugador
     */
    private void verificarMuerte() {
        Jugador player = contexto.getJugador();
        
        if (player == null || !player.estaVivo()) {
            return;
        }
        
        // Muerte por caída fuera del mapa
        if (player.getY() > canvas.getHeight() + 50) {
            System.out.println("[MUERTE] Jugador cayó fuera del mapa");
            player.recibirDanio(null);
        }
    }
    
    /**
     * Renderiza información de debug
     */
    private void renderDebug(Graphics g) {
        g.setColor(Color.GREEN);
        int y = 20;
        
        g.drawString("FPS: " + fps + " | TPS: " + tps, 10, y);
        y += 15;
        
        g.drawString("Objetos: " + contexto.getHandler().getGameObjs().size(), 10, y);
        y += 15;
        
        if (contexto.getGestorNiveles() != null) {
            g.setColor(Color.CYAN);
            g.drawString("Nivel: " + contexto.getGestorNiveles().getNivelActual(), 10, y);
            y += 15;
            
            String estadoNivel = contexto.getGestorNiveles().getEstadoActual()
                .getClass().getSimpleName();
            g.drawString("Estado Nivel: " + estadoNivel, 10, y);
            y += 15;
        }
        
        if (contexto.getGestorEstados() != null) {
            g.setColor(Color.MAGENTA);
            String estadoUI = contexto.getGestorEstados().getEstadoActual()
                .getClass().getSimpleName();
            g.drawString("Estado UI: " + estadoUI, 10, y);
            y += 15;
        }
        
        Jugador player = contexto.getJugador();
        if (player != null) {
            g.setColor(Color.YELLOW);
            g.drawString(String.format("Pos: (%.0f, %.0f)", player.getX(), player.getY()), 10, y);
            y += 15;
            
            Color colorEstado = player.estaVivo() ? Color.GREEN : Color.RED;
            g.setColor(colorEstado);
            g.drawString("Estado: " + player.getEstadoVida().getClass().getSimpleName(), 10, y);
        }
    }
    
    /**
     * Imprime estadísticas en consola
     */
    private void imprimirStats() {
        Jugador player = contexto.getJugador();
        
        System.out.println(String.format(
            "[STATS] FPS: %d | TPS: %d | Objetos: %d | Player: (%.0f, %.0f)",
            fps, tps,
            contexto.getHandler().getGameObjs().size(),
            player != null ? player.getX() : 0,
            player != null ? player.getY() : 0
        ));
    }
    
    // ==================== CONTROL ====================
    
    public void toggleDebug() {
        debug = !debug;
        System.out.println("[DEBUG] Modo debug: " + (debug ? "ACTIVADO" : "DESACTIVADO"));
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getFPS() {
        return fps;
    }
    
    public int getTPS() {
        return tps;
    }
}