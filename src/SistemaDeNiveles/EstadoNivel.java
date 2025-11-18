package SistemaDeNiveles;

import SistemaDeSoporte.Handler;
import SistemaDeSoporte.ObjetosID;
import Entidades.NPCs.DiegoKong;
import Entidades.NPCs.Princesa;
import Entidades.JuegoObjetos;
import Entidades.Jugador;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import mariotest.Juego;

/**
 * Patrón STATE para manejar estados del nivel
 * CORREGIDO: Renderiza sprites de corazón correctamente desde GestorNiveles
 */
public abstract class EstadoNivel {
    
    protected Juego juego;
    protected GestorNiveles gestorNiveles;
    
    public EstadoNivel(Juego juego, GestorNiveles gestorNiveles) {
        this.juego = juego;
        this.gestorNiveles = gestorNiveles;
    }
    
    public abstract void entrar();
    public abstract void tick();
    public abstract void render(Graphics g);
    public abstract void salir();
    
    public abstract boolean permitirMovimientoJugador();
    public abstract boolean permitirSpawnEnemigos();
    
    // ==================== ESTADO: JUGANDO ====================
    
    public static class Jugando extends EstadoNivel {
        
        public Jugando(Juego juego, GestorNiveles gestorNiveles) {
            super(juego, gestorNiveles);
        }
        
        @Override
        public void entrar() {
            System.out.println("[ESTADO NIVEL] → JUGANDO");
        }
        
        @Override
        public void tick() {
            if (gestorNiveles.verificarVictoria()) {
                gestorNiveles.cambiarEstado(new Victoria(juego, gestorNiveles));
            }
        }
        
        @Override
        public void render(Graphics g) {
            // Renderizado normal (manejado por Handler)
        }
        
        @Override
        public void salir() {
            System.out.println("[ESTADO NIVEL] JUGANDO → saliendo");
        }
        
        @Override
        public boolean permitirMovimientoJugador() {
            return true;
        }
        
        @Override
        public boolean permitirSpawnEnemigos() {
            return true;
        }
    }
    
    // ==================== ESTADO: VICTORIA ====================
    
    public static class Victoria extends EstadoNivel {
        
        private int ticksAnimacion;
        private static final int DURACION_ANIMACION = 240; // 4 segundos
        
        private DiegoKong diegoKong;
        private Princesa princesa;
        private TipoVictoria tipoVictoria;
        
        // Fases de animación
        private int faseActual;
        private static final int FASE_CORAZON = 0;          // 0-60 ticks
        private static final int FASE_DK_AGARRA = 1;        // 60-90 ticks
        private static final int FASE_MOVIMIENTO = 2;       // 90-180 ticks (DK sube)
        private static final int FASE_CORAZON_ROTO = 3;     // 180-240 ticks
        
        // Velocidad de escape
        private static final float VELOCIDAD_ESCAPE = -1.0f;
        
        public Victoria(Juego juego, GestorNiveles gestorNiveles) {
            super(juego, gestorNiveles);
            this.ticksAnimacion = 0;
            this.faseActual = FASE_CORAZON;
            
            int nivel = gestorNiveles.getNivelActual();
            this.tipoVictoria = (nivel >= 3) ? TipoVictoria.DERROTA_FINAL : TipoVictoria.ESCAPE_PRINCESA;
        }
        
        @Override
        public void entrar() {
            System.out.println("[ESTADO NIVEL] → VICTORIA (" + tipoVictoria + ")");
            
            gestorNiveles.detenerSpawners();
            
            if (juego.getHandler().getPlayer() != null) {
                juego.getHandler().getPlayer().detenerMovimiento();
            }
            
            obtenerReferenciasEntidades();
        }
        
        private void obtenerReferenciasEntidades() {
            Handler handler = juego.getHandler();
            
            for (JuegoObjetos obj : handler.getGameObjs()) {
                if (obj.getId() == ObjetosID.DiegoKong) {
                    diegoKong = (DiegoKong) obj;
                } else if (obj.getId() == ObjetosID.Princesa) {
                    princesa = (Princesa) obj;
                }
            }
            
            if (diegoKong == null) {
                System.err.println("[ERROR] DiegoKong no encontrado para animación");
            }
            if (princesa == null) {
                System.err.println("[ERROR] Princesa no encontrada para animación");
            }
        }
        
        @Override
        public void tick() {
            ticksAnimacion++;
            
            // Actualizar entidades
            if (diegoKong != null) {
                diegoKong.tick();
            }
            if (princesa != null) {
                princesa.tick();
            }
            
            // Ejecutar animación según tipo de victoria
            if (tipoVictoria == TipoVictoria.ESCAPE_PRINCESA) {
                tickAnimacionEscape();
            } else {
                tickAnimacionDerrotaFinal();
            }
            
            // Finalizar después de la duración
            if (ticksAnimacion >= DURACION_ANIMACION) {
                finalizarVictoria();
            }
        }
        
        /**
         * Animación de escape (niveles 1-2): DK agarra princesa y sube
         */
        private void tickAnimacionEscape() {
            if (ticksAnimacion == 60) {
                // FASE 1: DK agarra a la princesa
                faseActual = FASE_DK_AGARRA;
                if (diegoKong != null && princesa != null) {
                    diegoKong.activarAnimacionAgarrar();
                    princesa.moverHacia(diegoKong.getX() + 10, diegoKong.getY() + 5);
                    System.out.println("[VICTORIA] DK agarra a la princesa");
                }
            } 
            else if (ticksAnimacion >= 90 && ticksAnimacion < 180) {
                // FASE 2: MOVIMIENTO HACIA ARRIBA
                faseActual = FASE_MOVIMIENTO;
                moverDKYPrincesaHaciaArriba();
            } 
            else if (ticksAnimacion == 180) {
                // FASE 3: Corazón roto
                faseActual = FASE_CORAZON_ROTO;
                System.out.println("[VICTORIA] Corazón roto - ella escapa otra vez");
            }
        }
        
        /**
         * Animación de derrota final (nivel 3): Princesa libre
         */
        private void tickAnimacionDerrotaFinal() {
            if (ticksAnimacion == 60) {
                faseActual = FASE_DK_AGARRA; // Reutilizado como "DK derrotado"
                System.out.println("[VICTORIA] Diego Kong derrotado");
            } 
            else if (ticksAnimacion == 120) {
                faseActual = FASE_MOVIMIENTO; // Reutilizado como "Princesa libre"
                if (princesa != null && juego.getHandler().getPlayer() != null) {
                    Jugador mario = juego.getHandler().getPlayer();
                    princesa.moverHacia(mario.getX() + 20, mario.getY());
                    System.out.println("[VICTORIA] Princesa moviéndose hacia Mario");
                }
            } 
            else if (ticksAnimacion == 180) {
                faseActual = FASE_CORAZON_ROTO; // Reutilizado como "Victoria total"
                System.out.println("[VICTORIA] ¡Victoria total!");
            }
        }
        
        /**
         * Mueve a DK y la princesa hacia arriba (CRÍTICO)
         */
        private void moverDKYPrincesaHaciaArriba() {
            if (diegoKong != null) {
                // Mover DK hacia arriba
                diegoKong.setY(diegoKong.getY() + VELOCIDAD_ESCAPE);
                
                // Forzar a la princesa a seguir a DK
                if (princesa != null) {
                    // Detener movimiento automático si existe
                    if (princesa.isMoviendose()) {
                        princesa.detenerMovimiento();
                    }
                    
                    // Anclar posición a DK
                    float anclaX = diegoKong.getX() + 10;
                    float anclaY = diegoKong.getY() + 5;
                    princesa.setX(anclaX);
                    princesa.setY(anclaY);
                }
            }
        }
        
        private void finalizarVictoria() {
            int nivelActual = gestorNiveles.getNivelActual();
            
            System.out.println("[VICTORIA] Finalizando - Nivel: " + nivelActual);
            
            if (nivelActual >= 3) {
                // Nivel 3: Pantalla de victoria final
                System.out.println("[VICTORIA] → Cambiando a PantallaVictoria");
                
                if (juego.getGestorEstados() != null) {
                    juego.getGestorEstados().cambiarEstado(UI.EstadoJuegoEnum.VICTORIA);
                } else {
                    System.err.println("[ERROR] GestorEstados es NULL!");
                }
            } else {
                // Niveles 1-2: Siguiente nivel
                System.out.println("[VICTORIA] → Siguiente nivel");
                gestorNiveles.cambiarEstado(new Transicion(juego, gestorNiveles));
            }
        }
        
        @Override
        public void render(Graphics g) {
            // Overlay semi-transparente
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, Juego.getVentanaWidth(), Juego.getVentanaHeight());
            
            int centerX = Juego.getVentanaWidth() / 2;
            int centerY = Juego.getVentanaHeight() / 2;
            
            if (tipoVictoria == TipoVictoria.ESCAPE_PRINCESA) {
                renderOverlayEscape(g, centerX, centerY);
            } else {
                renderOverlayDerrotaFinal(g, centerX, centerY);
            }
            
            // Contador de tiempo
            if (ticksAnimacion > 180) {
                g.setColor(Color.CYAN);
                g.setFont(new Font("Arial", Font.BOLD, 18));
                String texto = "SIGUIENTE NIVEL EN " + ((DURACION_ANIMACION - ticksAnimacion) / 60 + 1) + "...";
                int w = g.getFontMetrics().stringWidth(texto);
                g.drawString(texto, centerX - w/2, Juego.getVentanaHeight() - 50);
            }
        }
        
        /**
         * Renderiza overlay de escape (niveles 1-2)
         * CORREGIDO: Obtiene sprites desde GestorNiveles
         */
        private void renderOverlayEscape(Graphics g, int centerX, int centerY) {
            switch (faseActual) {
                case FASE_CORAZON:
                    renderCorazon(g, centerX, centerY, false);
                    renderTexto(g, centerX, centerY + 80, "¡Nivel Completado!", Color.YELLOW);
                    break;
                    
                case FASE_DK_AGARRA:
                    renderTexto(g, centerX, centerY, "¡DIEGO KONG AGARRA A LA PRINCESA!", Color.RED);
                    break;
                    
                case FASE_MOVIMIENTO:
                    renderTexto(g, centerX, centerY, "¡LA ESTÁ LLEVANDO!", Color.ORANGE);
                    break;
                    
                case FASE_CORAZON_ROTO:
                    renderCorazon(g, centerX, centerY, true);
                    renderTexto(g, centerX, centerY + 80, "¡Ella se escapa otra vez!", new Color(255, 100, 100));
                    break;
            }
        }
        
        /**
         * Renderiza overlay de derrota final (nivel 3)
         * CORREGIDO: Obtiene sprites desde GestorNiveles
         */
        private void renderOverlayDerrotaFinal(Graphics g, int centerX, int centerY) {
            switch (faseActual) {
                case FASE_CORAZON:
                    renderCorazon(g, centerX, centerY, false);
                    renderTexto(g, centerX, centerY + 80, "¡VICTORIA!", new Color(255, 215, 0));
                    break;
                    
                case FASE_DK_AGARRA:
                    renderTexto(g, centerX, centerY, "¡DIEGO KONG DERROTADO!", new Color(255, 215, 0));
                    break;
                    
                case FASE_MOVIMIENTO:
                    renderTexto(g, centerX, centerY, "¡LA PRINCESA ES LIBRE!", new Color(255, 105, 180));
                    break;
                    
                case FASE_CORAZON_ROTO:
                    renderCorazon(g, centerX, centerY, false); // Corazón COMPLETO
                    renderTexto(g, centerX, centerY + 80, "¡VICTORIA TOTAL!", new Color(0, 255, 127));
                    break;
            }
        }
        
        /**
         * Renderiza el corazón
         * CRÍTICO: Obtiene sprites desde GestorNiveles
         */
        private void renderCorazon(Graphics g, int centerX, int centerY, boolean roto) {
            // Obtener sprite desde GestorNiveles
            BufferedImage sprite = roto ? 
                gestorNiveles.getSpriteCorazonRoto() : 
                gestorNiveles.getSpriteCorazon();
            
            if (sprite != null) {
                // Escala 4x para que sea grande y visible
                int size = 80;
                int x = centerX - size/2;
                int y = centerY - size/2;
                
                g.drawImage(sprite, x, y, size, size, null);
                
            } else {
                
                // Corazón placeholder más grande y visible
                int size = 60;
                g.setColor(roto ? new Color(139, 0, 0) : new Color(255, 0, 0));
                
                // Dibujar corazón con forma más reconocible
                int[] xPoints = {centerX, centerX - size/2, centerX - size/2, centerX, 
                                centerX + size/2, centerX + size/2};
                int[] yPoints = {centerY + size/2, centerY, centerY - size/3, centerY - size/4, 
                                centerY - size/3, centerY};
                g.fillPolygon(xPoints, yPoints, 6);
                
                if (roto) {
                    // X grande para corazón roto
                    g.setColor(Color.BLACK);
                    g.drawLine(centerX - size/2, centerY - size/2, centerX + size/2, centerY + size/2);
                    g.drawLine(centerX + size/2, centerY - size/2, centerX - size/2, centerY + size/2);
                }
                
                // Texto de advertencia
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.PLAIN, 12));
                String warning = "(Sprite no cargado)";
                int w = g.getFontMetrics().stringWidth(warning);
                g.drawString(warning, centerX - w/2, centerY + size);
            }
        }
        
        /**
         * Renderiza texto con sombra
         */
        private void renderTexto(Graphics g, int centerX, int centerY, String texto, Color color) {
            g.setFont(new Font("Arial", Font.BOLD, 28));
            
            int w = g.getFontMetrics().stringWidth(texto);
            int x = centerX - w/2;
            
            // Sombra
            g.setColor(Color.BLACK);
            g.drawString(texto, x + 2, centerY + 2);
            
            // Texto principal
            g.setColor(color);
            g.drawString(texto, x, centerY);
        }
        
        @Override
        public void salir() {
            System.out.println("[ESTADO NIVEL] VICTORIA → saliendo");
        }
        
        @Override
        public boolean permitirMovimientoJugador() {
            return false;
        }
        
        @Override
        public boolean permitirSpawnEnemigos() {
            return false;
        }
    }
    
    // ==================== ESTADO: TRANSICION ====================
    
    public static class Transicion extends EstadoNivel {
        
        private int ticksTransicion;
        private static final int DURACION_FADE = 60;
        private float alphaFade;
        
        public Transicion(Juego juego, GestorNiveles gestorNiveles) {
            super(juego, gestorNiveles);
            this.ticksTransicion = 0;
            this.alphaFade = 0f;
        }
        
        @Override
        public void entrar() {
            System.out.println("[ESTADO NIVEL] → TRANSICION");
        }
        
        @Override
        public void tick() {
            ticksTransicion++;
            alphaFade = Math.min(1f, (float)ticksTransicion / DURACION_FADE);
            
            if (ticksTransicion >= DURACION_FADE) {
                gestorNiveles.cambiarEstado(new CargandoNivel(juego, gestorNiveles));
            }
        }
        
        @Override
        public void render(Graphics g) {
            Color colorFade = new Color(0, 0, 0, (int)(alphaFade * 255));
            g.setColor(colorFade);
            g.fillRect(0, 0, Juego.getVentanaWidth(), Juego.getVentanaHeight());
        }
        
        @Override
        public void salir() {
            System.out.println("[ESTADO NIVEL] TRANSICION → saliendo");
        }
        
        @Override
        public boolean permitirMovimientoJugador() {
            return false;
        }
        
        @Override
        public boolean permitirSpawnEnemigos() {
            return false;
        }
    }
    
    // ==================== ESTADO: CARGANDO NIVEL ====================
    
    public static class CargandoNivel extends EstadoNivel {
        
        public CargandoNivel(Juego juego, GestorNiveles gestorNiveles) {
            super(juego, gestorNiveles);
        }
        
        @Override
        public void entrar() {
            System.out.println("[ESTADO NIVEL] → CARGANDO_NIVEL");
            gestorNiveles.cargarSiguienteNivel();
            gestorNiveles.cambiarEstado(new Jugando(juego, gestorNiveles));
        }
        
        @Override
        public void tick() {
            // Se ejecuta solo una vez
        }
        
        @Override
        public void render(Graphics g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, Juego.getVentanaWidth(), Juego.getVentanaHeight());
            
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 24));
            String texto = "CARGANDO NIVEL...";
            int x = Juego.getVentanaWidth() / 2 - 100;
            int y = Juego.getVentanaHeight() / 2;
            g.drawString(texto, x, y);
        }
        
        @Override
        public void salir() {
            System.out.println("[ESTADO NIVEL] CARGANDO_NIVEL → saliendo");
        }
        
        @Override
        public boolean permitirMovimientoJugador() {
            return false;
        }
        
        @Override
        public boolean permitirSpawnEnemigos() {
            return false;
        }
    }
}