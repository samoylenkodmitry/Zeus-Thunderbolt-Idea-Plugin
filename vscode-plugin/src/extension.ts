import { log } from 'console';
import * as vscode from 'vscode';

// Core particle system types and interfaces
interface PhysicsElement {
  x0: number;
  y0: number;
  x: number;
  y: number;
  chainStrength: number;
  isDead: boolean;
  update(elements: PhysicsElement[]): void;
  render(ctx: CanvasRenderingContext2D): void;
  reset(): void;
}

// https://google.com
// Core types
interface Force {
  x: number;
  y: number;
}

enum Theme {
  None,
  NorthernLights,
  CyberPunk,
  DeepOcean,
  Spectrum,
  LavaFlow,
  NeonCity,
  Twilight,
  VaporWave,
  BlackSnow,
  Matrix
}

// Add missing class definitions
class Color {
  constructor(
    public readonly r: number,
    public readonly g: number,
    public readonly b: number,
    public readonly a: number = 255
  ) { }

  toString(): string {
    return `rgba(${this.r}, ${this.g}, ${this.b}, ${this.a / 255})`;
  }
}

class Snowflake implements PhysicsElement {
  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    public size: number,
    public rotationSpeed: number,
    public swayFrequency: number,
    public swayAmplitude: number,
    public descendSpeed: number,
    public chainStrength: number,
    public lifetime: number,
    public windInfluence: number,
    public turbulence: number,
    public sparklePhase: number,
    public sparkleSpeed: number,
    public layer: number,
    public spinPhase: number,
    public spinSpeed: number
  ) { }

  public isDead = false;

  update(_elements: PhysicsElement[]): void {
    this.lifetime -= dt.value;
    if (this.lifetime <= 0) {
      this.isDead = true;
      return;
    }

    const layerFactor = 1 - (this.layer / 3);
    const baseSpeed = this.descendSpeed * (0.7 + 0.3 * layerFactor);

    // Update spin
    this.spinPhase += this.spinSpeed * dt.value;
    const spinOffset = Math.sin(this.spinPhase) * this.size * 0.2;

    // Access global wind force instead of local property
    this.x += globalState.currentWindForce * this.windInfluence * layerFactor * dt.value;
    this.x += spinOffset * dt.value;
    this.y += baseSpeed * dt.value;

    // Reset if out of bounds
    if (this.y > 1000 || this.x < -200 || this.x > 1200) {
      this.reset();
      this.x = Math.random() * 1000;
      this.y = -10;
    }
  }

  render(ctx: CanvasRenderingContext2D): void {
    ctx.save();
    ctx.translate(this.x, this.y);

    // Sparkle effect
    this.sparklePhase += dt.value * this.sparkleSpeed;
    const sparkle = (Math.sin(this.sparklePhase) * 0.3 + 0.7);

    const alpha = Math.min(255, (255 * (this.lifetime / 5)) * sparkle);
    ctx.strokeStyle = `rgba(255, 255, 255, ${alpha / 255})`;
    ctx.lineWidth = 1;

    // Draw snowflake
    const points = 6;
    for (let i = 0; i < points; i++) {
      const angle = (i * Math.PI * 2) / points;
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.lineTo(
        Math.cos(angle) * this.size,
        Math.sin(angle) * this.size
      );
      ctx.stroke();

      // Draw branches
      const midX = Math.cos(angle) * this.size * 0.5;
      const midY = Math.sin(angle) * this.size * 0.5;
      ctx.beginPath();
      ctx.moveTo(midX, midY);
      const nextAngle = ((i + 2) % points * Math.PI * 2) / points;
      ctx.lineTo(
        midX + Math.cos(nextAngle) * this.size * 0.3,
        midY + Math.sin(nextAngle) * this.size * 0.3
      );
      ctx.stroke();
    }

    ctx.restore();
  }

  reset(): void {
    this.isDead = false;
    this.lifetime = 5;
  }
}

class Particle implements PhysicsElement {
  private trail: Array<{ x: number, y: number }> = new Array(10).fill({ x: 0, y: 0 });
  private trailIndex = -1;
  private trailSize = 0;
  public isDead = false;
  private lifetime: number = 2;

  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    public size: number,
    public color: Color,
    public force: Force,
    public chainStrength: number = 0
  ) { }

  update(_elements: PhysicsElement[]): void {
    this.lifetime -= dt.value;
    if (this.lifetime <= 0) {
      this.isDead = true;
      return;
    }

    // Update trail
    this.trailIndex = (this.trailIndex + 1) % this.trail.length;
    this.trail[this.trailIndex] = { x: this.x, y: this.y };
    this.trailSize = Math.min(this.trailSize + 1, this.trail.length);

    // Physics updates
    this.x += this.force.x * dt.value;
    this.y += this.force.y * dt.value;

    // Apply gravity
    this.force.y += 300 * dt.value;

    // Apply friction
    const frictionFactor = 0.95 + (Math.random() * 0.04 - 0.02);
    this.force.x *= frictionFactor;
    this.force.y *= frictionFactor;
  }

  render(ctx: CanvasRenderingContext2D): void {
    ctx.save();

    // Draw trail
    if (this.trailSize > 1) {
      ctx.beginPath();
      ctx.strokeStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${(50 * (this.lifetime / 2)) / 255})`;
      ctx.lineWidth = this.size / 4;

      let i = this.trailIndex;
      ctx.moveTo(this.trail[i].x, this.trail[i].y);
      for (let count = 0; count < this.trailSize; count++) {
        i = (i - 1 + this.trail.length) % this.trail.length;
        ctx.lineTo(this.trail[i].x, this.trail[i].y);
      }
      ctx.stroke();
    }

    // Draw particle
    ctx.fillStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${Math.min(255, 255 * (this.lifetime / 2)) / 255})`;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size / 2, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  reset(): void {
    this.isDead = false;
    // Reset other properties
  }
}

class StardustParticle implements PhysicsElement {
  public isDead = false;
  private trailPoints: Array<{ x: number, y: number }> = new Array(20).fill({ x: 0, y: 0 });
  private trailIndex = 0;
  private trailSize = 0;
  private twinklePhase = Math.random() * Math.PI * 2;
  private rotationAngle = Math.random() * Math.PI * 2;
  private colorTransitionPhase = Math.random() * Math.PI * 2;
  private lifetime: number = 3;

  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    public size: number,
    public baseColor: Color,
    public driftSpeed: number = 3,
    public chainStrength: number = 0,
    public color: Color = baseColor
  ) { }

  update(_elements: PhysicsElement[]): void {
    this.lifetime -= dt.value;
    if (this.lifetime <= 0) {
      this.isDead = true;
      return;
    }

    // Update trail
    this.trailIndex = (this.trailIndex + 1) % this.trailPoints.length;
    this.trailPoints[this.trailIndex] = { x: this.x, y: this.y };
    this.trailSize = Math.min(this.trailSize + 1, this.trailPoints.length);

    // Color transition
    this.colorTransitionPhase += dt.value * 0.5;

    // Enhanced twinkling effect
    this.twinklePhase += (8 + Math.sin(this.colorTransitionPhase) * 4) * dt.value;

    // Smooth drifting motion with spiral tendency
    this.rotationAngle += dt.value * (0.5 + Math.sin(this.twinklePhase * 0.5) * 0.3);
    const spiralRadius = 30 + Math.sin(this.twinklePhase * 0.3) * 10;
    this.x += (Math.cos(this.rotationAngle) * spiralRadius + Math.sin(this.twinklePhase * 2) * 5) * dt.value;
    this.y += (Math.sin(this.rotationAngle) * spiralRadius + Math.cos(this.twinklePhase * 2) * 5) * dt.value;
  }

  render(ctx: CanvasRenderingContext2D): void {
    ctx.save();

    // Draw trail with gradient
    if (this.trailSize > 1) {
      ctx.beginPath();
      ctx.strokeStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, 0.2)`;
      ctx.lineWidth = this.size / 4;
      let i = this.trailIndex;
      ctx.moveTo(this.trailPoints[i].x, this.trailPoints[i].y);
      for (let count = 0; count < this.trailSize; count++) {
        i = (i - 1 + this.trailPoints.length) % this.trailPoints.length;
        ctx.lineTo(this.trailPoints[i].x, this.trailPoints[i].y);
      }
      ctx.stroke();
    }

    // Enhanced twinkle effect
    const twinkle = (Math.sin(this.twinklePhase) * 0.5 + 0.5);
    const alpha = Math.min(255, 255 * this.lifetime / 3 * twinkle);

    // Draw star core
    ctx.fillStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${alpha / 255})`;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    ctx.fill();

    // Draw glow
    const gradient = ctx.createRadialGradient(
      this.x, this.y, 0,
      this.x, this.y, this.size * 3
    );
    gradient.addColorStop(0, `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${alpha / 255})`);
    gradient.addColorStop(1, `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, 0)`);
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size * 3, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  reset(): void {
    this.isDead = false;
  }
}

class Butterfly implements PhysicsElement {
  public isDead = false;
  private flapPhase = Math.random() * Math.PI * 2;
  private pathPhase = Math.random() * Math.PI * 2;
  private verticalPhase = Math.random() * Math.PI * 2;
  private pathSpeed: number;
  private pathRadius: number;
  private verticalSpeed: number;

  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    public size: number,
    public color: Color,
    public wingSpan: number,
    public flapSpeed: number,
    public lifetime: number,
    public chainStrength: number = 0,
    public spotColor: Color,
    public patternColor: Color,
    pathRadius: number = 30,
    pathSpeed: number = 1,
    verticalSpeed: number = 0.5
  ) {
    this.pathRadius = pathRadius;
    this.pathSpeed = pathSpeed;
    this.verticalSpeed = verticalSpeed;
  }

  update(_elements: PhysicsElement[]): void {
    this.lifetime -= dt.value;
    if (this.lifetime <= 0) {
      this.isDead = true;
      return;
    }

    // Wing flapping animation
    this.flapPhase += this.flapSpeed * dt.value;

    // Complex flight path
    this.pathPhase += this.pathSpeed * dt.value;
    this.verticalPhase += this.verticalSpeed * dt.value;

    // Calculate new position
    this.x += (Math.cos(this.pathPhase) * this.pathRadius * dt.value +
      Math.sin(this.pathPhase * 0.5) * 10 * dt.value);
    this.y += (Math.sin(this.pathPhase * 0.7) * this.pathRadius * 0.5 * dt.value +
      Math.sin(this.verticalPhase) * 15 * dt.value);
  }

  render(ctx: CanvasRenderingContext2D): void {
    ctx.save();

    // Calculate alpha based on lifetime
    const alpha = Math.min(255, 255 * (this.lifetime / 8));

    // Move to butterfly position
    ctx.translate(this.x, this.y);

    // Draw wings
    const leftWingAngle = Math.sin(this.flapPhase) * Math.PI * 1.3;
    const rightWingAngle = -leftWingAngle;

    // Draw left wing
    ctx.save();
    ctx.rotate(leftWingAngle);
    this.drawWing(ctx, -1, alpha);
    ctx.restore();

    // Draw right wing
    ctx.save();
    ctx.rotate(rightWingAngle);
    this.drawWing(ctx, 1, alpha);
    ctx.restore();

    // Draw body
    ctx.fillStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${alpha / 255})`;
    ctx.beginPath();
    ctx.ellipse(0, 0, this.size / 3, this.size, 0, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  private drawWing(ctx: CanvasRenderingContext2D, direction: number, alpha: number): void {
    const wingPath = new Path2D();
    wingPath.moveTo(0, 0);
    wingPath.quadraticCurveTo(
      direction * this.wingSpan * 0.5, -this.size,
      direction * this.wingSpan, 0
    );
    wingPath.quadraticCurveTo(
      direction * this.wingSpan * 0.5, this.size,
      0, 0
    );

    // Draw wing base color
    ctx.fillStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${alpha / 255})`;
    ctx.fill(wingPath);

    // Draw patterns
    ctx.fillStyle = `rgba(${this.patternColor.r}, ${this.patternColor.g}, ${this.patternColor.b}, ${alpha / 255 * 0.7})`;
    ctx.beginPath();
    ctx.arc(
      direction * this.wingSpan * 0.6,
      0,
      this.size * 0.4,
      0,
      Math.PI * 2
    );
    ctx.fill();
  }

  reset(): void {
    this.isDead = false;
  }
}

class ChainParticle implements PhysicsElement {
  public isDead = false;
  private vibePhase = Math.random() * Math.PI * 2;
  private vibeFrequency = Math.random() * 5 + 10;
  private vibeAmplitude = Math.random() * 2 + 2;

  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    public size: number,
    public color: Color,
    public force: Force,
    public friction: number = 0.99,
    public lifetime: number = 0.5,
    public chainStrength: number = 0.8,
    public maxChainDistance: number = 100,
    public originalX: number = x,
    public originalY: number = y
  ) { }

  update(elements: PhysicsElement[]): void {
    this.lifetime -= dt.value;
    if (this.lifetime <= 0) {
      this.isDead = true;
      return;
    }

    // Electric vibration
    this.vibePhase += this.vibeFrequency * dt.value;
    const vibeOffsetX = Math.sin(this.vibePhase) * this.vibeAmplitude;
    const vibeOffsetY = Math.cos(this.vibePhase * 1.5) * this.vibeAmplitude;

    // Gradually return to original position with elasticity
    const returnStrength = 5;
    this.force.x += (this.originalX - this.x) * returnStrength * dt.value;
    this.force.y += (this.originalY - this.y) * returnStrength * dt.value;

    // Almost no gravity
    this.force.y += 20 * dt.value;

    // Basic physics with vibration
    this.x += this.force.x * dt.value + vibeOffsetX;
    this.y += this.force.y * dt.value + vibeOffsetY;

    // Stronger friction to stay in place better
    this.force.x *= 0.95;
    this.force.y *= 0.95;

    // Chain behavior
    for (const other of elements) {
      if (other !== this && other.chainStrength > 0) {
        const dx = other.x - this.x;
        const dy = other.y - this.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < this.maxChainDistance) {
          const strength = (1 - distance / this.maxChainDistance) * this.chainStrength;
          this.force.x += dx * strength * dt.value;
          this.force.y += dy * strength * dt.value;
        }
      }
    }
  }

  render(ctx: CanvasRenderingContext2D): void {
    ctx.save();

    // Draw connections to nearby particles
    ctx.lineWidth = this.size / 3;

    // Draw chain particle
    const alpha = Math.min(255, 255 * (this.lifetime / 3) * this.chainStrength);
    ctx.fillStyle = `rgba(${this.color.r}, ${this.color.g}, ${this.color.b}, ${alpha / 255})`;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size / 2, 0, Math.PI * 2);
    ctx.fill();

    ctx.restore();
  }

  reset(): void {
    this.isDead = false;
    this.lifetime = 0.5;
  }
}

// Add global dt variable for particle updates (similar to Kotlin implementation)
const dt = {
  value: 0
};

// Add global wind force variable
const globalState = {
  currentWindForce: 0
};

class ParticleSystem {
  private static readonly TARGET_FPS = 60;
  private static readonly FRAME_TIME_MS = 1000 / ParticleSystem.TARGET_FPS;
  private static readonly MAX_PARTICLES = 2500;
  private static readonly MAX_CHAIN_PARTICLES = 30;
  private static readonly WIND_CHANGE_INTERVAL = 2;
  private static readonly MAX_WIND_FORCE = 100;
  private static readonly SNOW_FADE_OUT_TIME = 5;  // Time in seconds after typing stops
  private static readonly SNOW_SPAWN_RATE = 0.1;   // Time between snowflake spawns
  private static readonly MAX_ACTIVE_SNOWFLAKES = 100;
  private static readonly TYPING_COOLDOWN = 0.1;
  private static readonly MIN_SNOW_SPAWN = 2;
  private static readonly MAX_SNOW_SPAWN = 5;
  private static readonly SNOW_LAYERS = 3;

  private elements: PhysicsElement[] = [];
  private currentWindForce = 0;
  private targetWindForce = 0;
  private windTimer = 0;
  private lastFrameTime = Date.now();
  private animationFrameId: number | null = null;
  private canvas!: HTMLCanvasElement;
  private ctx!: CanvasRenderingContext2D;
  private currentTheme: Theme = Theme.None;
  private snowEnabled = false;
  private regularParticlesEnabled = true;
  private stardustParticlesEnabled = false;
  private reverseParticlesEnabled = false;
  private butterflyParticlesEnabled = false;
  private isSnowing = false;
  private lastTypingTime = 0;
  private snowSpawnAccumulator = 0;
  private typingSpeed = 0;
  private lastTypeTime = 0;
  private typeCount = 0;
  private particlePool = new ParticlePool(3000);
  private x0: number = 0;
  private y0: number = 0;
  private disposables: vscode.Disposable[] = [];

  constructor() {
    console.log('ParticleSystem constructor called');
    try {
      // Create canvas element
      this.canvas = document.createElement('canvas');
      this.canvas.style.position = 'fixed';
      this.canvas.style.top = '0';
      this.canvas.style.left = '0';
      this.canvas.style.pointerEvents = 'none';
      this.canvas.style.zIndex = '9999';
      document.body.appendChild(this.canvas);

      // Get canvas context
      const ctx = this.canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Could not get 2D rendering context');
      }
      this.ctx = ctx;

      // Handle window resize
      window.addEventListener('resize', () => this.updateCanvasSize());
      this.updateCanvasSize();
    } catch (error) {
      console.error('Error in ParticleSystem constructor:', error);
      throw error; // Re-throw to prevent partially initialized state
    }
  }

  private updateWindAndSnow() {
    if (!this.snowEnabled) return;

    this.windTimer += dt.value;
    if (this.windTimer >= ParticleSystem.WIND_CHANGE_INTERVAL) {
      this.windTimer = 0;
      this.targetWindForce = (Math.random() * 2 - 1) * ParticleSystem.MAX_WIND_FORCE;
    }
    this.currentWindForce += (this.targetWindForce - this.currentWindForce) * dt.value * 2;
    // Update global wind force
    globalState.currentWindForce = this.currentWindForce;
  }

  private updateTypingIntensity(): void {
    const currentTime = Date.now() * 0.001;
    if (currentTime - this.lastTypeTime > ParticleSystem.TYPING_COOLDOWN) {
      this.typeCount = Math.max(0, this.typeCount - 1);
    }
    this.typingSpeed = Math.min(10, this.typeCount) / 10;
  }

  private updateSnow(): void {
    const currentTime = Date.now() * 0.001;
    this.updateTypingIntensity();

    // Check if we should stop snowing
    if (this.isSnowing && currentTime - this.lastTypingTime > ParticleSystem.SNOW_FADE_OUT_TIME) {
      this.isSnowing = false;
    }

    if (this.isSnowing) {
      this.snowSpawnAccumulator += dt.value;
      while (this.snowSpawnAccumulator >= ParticleSystem.SNOW_SPAWN_RATE) {
        this.snowSpawnAccumulator -= ParticleSystem.SNOW_SPAWN_RATE;

        const currentSnowflakes = this.elements.filter(e => e instanceof Snowflake).length;
        if (currentSnowflakes < ParticleSystem.MAX_ACTIVE_SNOWFLAKES) {
          const spawnCount = ParticleSystem.MIN_SNOW_SPAWN +
            Math.floor((ParticleSystem.MAX_SNOW_SPAWN - ParticleSystem.MIN_SNOW_SPAWN) * this.typingSpeed);

          for (let i = 0; i < spawnCount; i++) {
            const randomX = Math.random() * 1200 - 100;
            const layer = Math.floor(Math.random() * ParticleSystem.SNOW_LAYERS);
            this.elements.push(this.generateSnowflake({ x: randomX, y: 0 }, layer));
          }
        }
      }
    }
  }

  private generateSnowflake(point: { x: number, y: number }, layer: number = 0): Snowflake {
    const size = Math.random() < 0.6 ?
      2 + Math.random() * 2 :  // 60% small flakes
      Math.random() < 0.75 ?
        4 + Math.random() * 2 :  // 30% medium flakes
        6 + Math.random() * 2;   // 10% large flakes

    return new Snowflake(
      this.x0, this.y0,
      point.x, point.y,
      size,
      -1 + Math.random() * 2 * 0.8,
      0.5 + Math.random() * 1.5,
      2 + Math.random() * 3,
      Math.min(60, 30 + size * 3),
      0,
      8 + Math.random() * 4,
      0.8 + Math.random() * 0.4,
      0.5 + Math.random(),
      Math.random() * Math.PI * 2,
      4 + Math.random() * 4,
      layer,
      Math.random() * Math.PI * 2,
      2 + Math.random() * 2
    );
  }

  public setTheme(themeName: string) {
    this.currentTheme = Theme[themeName as keyof typeof Theme] || Theme.None;
  }

  public setSnowEnabled(enabled: boolean) {
    this.snowEnabled = enabled;
  }

  public setRegularParticlesEnabled(enabled: boolean) {
    this.regularParticlesEnabled = enabled;
  }

  public setStardustParticlesEnabled(enabled: boolean) {
    this.stardustParticlesEnabled = enabled;
  }

  public setReverseParticlesEnabled(enabled: boolean) {
    this.reverseParticlesEnabled = enabled;
  }

  public setButterfliesEnabled(enabled: boolean) {
    this.butterflyParticlesEnabled = enabled;
  }

  public isReverseParticlesEnabled(): boolean {
    return this.reverseParticlesEnabled;
  }

  private animate() {
    try {
      const currentTime = Date.now();
      dt.value = Math.min((currentTime - this.lastFrameTime) / 1000, 0.032);
      this.lastFrameTime = currentTime;

      // Update particles
      this.updateWindAndSnow();
      this.elements = this.elements.filter(e => !e.isDead);
      this.elements.forEach(e => e.update(this.elements));

      // Clear canvas
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

      // Render particles
      this.elements.forEach(e => e.render(this.ctx));

      this.animationFrameId = window.requestAnimationFrame(() => this.animate());
    } catch (error) {
      console.error('Error in animate:', error);
    }
  }

  public start() {
    console.log('ParticleSystem.start() called');
    this.animate();
  }

  public stop() {
    if (this.animationFrameId) {
      window.cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
    if (this.canvas.parentElement) {
      this.canvas.parentElement.removeChild(this.canvas);
    }
  }

  private updateCanvasSize() {
    try {
      const dpr = window.devicePixelRatio || 1;
      const width = window.innerWidth;
      const height = window.innerHeight;

      this.canvas.width = width * dpr;
      this.canvas.height = height * dpr;

      this.ctx.scale(dpr, dpr);
      console.log(`Canvas resized: ${width}x${height} (DPR: ${dpr})`);
    } catch (error) {
      console.error('Error in updateCanvasSize:', error);
    }
  }

  private generateParticles(point: vscode.Position, count: number = 10): PhysicsElement[] {
    const screenPoint = this.editorPositionToScreen(point);
    return Array(count).fill(null).map(() => {
      const random = Math.random();
      if (this.snowEnabled && random > 0.7 && random < 0.9) {
        return this.generateSnowflake(screenPoint);
      } else if (this.stardustParticlesEnabled && random > 0.8) {
        return this.generateStardustParticle(screenPoint);
      } else if (this.butterflyParticlesEnabled && random > 0.95) {
        return this.generateButterfly(screenPoint);
      } else if (this.regularParticlesEnabled) {
        return this.generateRegularParticle(screenPoint);
      }
      return null;
    }).filter(p => p !== null) as PhysicsElement[];
  }

  private generateRegularParticle(point: { x: number, y: number }): Particle {
    const angle = Math.random() * Math.PI * 2;
    const speed = 100 + Math.random() * 100;
    return new Particle(
      this.x0, this.y0,
      point.x, point.y,
      Math.random() * 3 + 2,
      new Color(
        Math.random() * 255,
        Math.random() * 255,
        Math.random() * 255
      ),
      {
        x: Math.cos(angle) * speed,
        y: Math.sin(angle) * speed
      }
    );
  }

  private generateStardustParticle(point: { x: number, y: number }): StardustParticle {
    const baseColor = Math.random() < 0.33 ?
      new Color(255, 223, 170) :  // Warm gold
      Math.random() < 0.5 ?
        new Color(200, 255, 255) :  // Ice blue
        new Color(255, 200, 255);   // Pink

    return new StardustParticle(
      this.x0, this.y0,
      point.x, point.y,
      3 + Math.random() * 3,
      baseColor,
      3,
      0,
      baseColor
    );
  }

  private generateButterfly(point: { x: number, y: number }): Butterfly {
    const [mainColor, spotColor, patternColor] = this.getRandomButterflyColors();
    return new Butterfly(
      this.x0, this.y0,
      point.x, point.y,
      Math.random() * 4 + 8,  // size
      mainColor,
      Math.random() * 10 + 15,  // wingSpan
      Math.random() * 2 + 2,    // flapSpeed
      Math.random() * 3 + 5,    // lifetime
      0,                        // chainStrength
      spotColor,
      patternColor
    );
  }

  private getRandomButterflyColors(): [Color, Color, Color] {
    const schemes = [
      [  // Monarch
        new Color(255, 140, 0),  // Orange
        new Color(0, 0, 0),      // Black
        new Color(255, 255, 255) // White
      ],
      [  // Blue Morpho
        new Color(0, 150, 255),  // Blue
        new Color(0, 100, 200),  // Dark blue
        new Color(200, 230, 255) // Light blue
      ],
      // Add more color schemes as needed
    ] as [Color, Color, Color][];  // Type assertion
    return schemes[Math.floor(Math.random() * schemes.length)];
  }

  handleCursorMove(position: vscode.Position): void {
    console.log('Cursor moved:', position);
    const particles = this.generateParticles(position, 20); // Increased particle count
    this.elements.push(...particles);
    this.trimParticles();
  }

  handleDeletion(change: vscode.TextDocumentContentChangeEvent): void {
    const editor = vscode.window.activeTextEditor;
    if (!editor) return;

    const deletePosition = editor.document.positionAt(change.rangeOffset);
    const point = this.editorPositionToScreen(deletePosition);

    const reverseParticle = this.generateReverseParticle(point);
    this.elements.push(reverseParticle);
    this.trimParticles();
  }

  private generateReverseParticle(point: { x: number, y: number }): ReverseParticle {
    // Create a group of particles to simulate together
    const particleGroup = Array(10).fill(null).map(() =>
      this.generateRegularParticle(point)
    );

    const snapshots: ParticleSnapshot[] = [];
    const lifetime = 90;  // Number of frames to simulate

    // Simulate particles together
    for (let frame = 0; frame < lifetime; frame++) {
      // Update all particles together so they interact
      particleGroup.forEach(p => p.update(particleGroup));

      // Save snapshot of all particles
      snapshots.push({
        particles: particleGroup.map(p => {
          const copy = new Particle(
            p.x0, p.y0, p.x, p.y,
            p.size, p.color, p.force,
            p.chainStrength
          );
          copy.isDead = p.isDead;
          return copy;
        })
      });
    }

    return new ReverseParticle(
      this.x0, this.y0,
      point.x, point.y,
      snapshots.reverse()
    );
  }

  private editorPositionToScreen(position: vscode.Position): { x: number, y: number } {
    const editor = vscode.window.activeTextEditor;
    if (!editor) return { x: 0, y: 0 };

    // Get editor configuration
    const config = vscode.workspace.getConfiguration('editor');
    const fontSize = config.get<number>('fontSize', 14);
    const lineHeight = fontSize * 1.5;
    const charWidth = fontSize * 0.6;

    // Get visible ranges to calculate scroll offset
    const visibleRanges = editor.visibleRanges;
    if (visibleRanges.length === 0) return { x: 0, y: 0 };

    // Calculate position relative to first visible line
    const firstVisibleLine = visibleRanges[0].start.line;
    const relativeY = (position.line - firstVisibleLine) * lineHeight;

    return {
      x: position.character * charWidth,
      y: relativeY
    };
  }

  private trimParticles(): void {
    if (this.elements.length > ParticleSystem.MAX_PARTICLES) {
      this.elements = this.elements.slice(
        this.elements.length - ParticleSystem.MAX_PARTICLES
      );
    }
  }

  private readonly themeColors: Map<Theme, Color[]> = new Map([
    [Theme.NorthernLights, [
      new Color(0, 255, 127),    // Spring Green
      new Color(64, 224, 208),   // Turquoise
      new Color(0, 191, 255),    // Deep Sky Blue
      new Color(138, 43, 226),   // Blue Violet
      new Color(75, 0, 130),     // Indigo
      new Color(0, 250, 154)     // Medium Spring Green
    ]],
    [Theme.CyberPunk, [
      new Color(255, 0, 128),    // Hot Pink
      new Color(0, 255, 255),    // Cyan
      new Color(255, 255, 0),    // Yellow
      new Color(128, 0, 255),    // Purple
      new Color(255, 128, 0)     // Orange
    ]],
    [Theme.Matrix, [
      new Color(0, 255, 0),      // Bright Green
      new Color(0, 200, 0),      // Medium Green
      new Color(0, 150, 0)       // Dark Green
    ]],
    [Theme.BlackSnow, [
      new Color(0, 0, 0),        // Pure black
      new Color(20, 20, 20),     // Dark gray
      new Color(40, 40, 40)      // Medium gray
    ]],
    [Theme.DeepOcean, [
      new Color(0, 105, 148),   // Deep Blue
      new Color(0, 154, 184),   // Medium Blue
      new Color(64, 224, 208),  // Turquoise
      new Color(127, 255, 212), // Aquamarine
      new Color(0, 206, 209),   // Dark Turquoise
      new Color(70, 130, 180),  // Steel Blue
      new Color(173, 216, 230)  // Light Blue
    ]],
    [Theme.Spectrum, [
      new Color(255, 0, 0),     // Red
      new Color(255, 127, 0),   // Orange
      new Color(255, 255, 0),   // Yellow
      new Color(0, 255, 0),     // Green
      new Color(0, 0, 255),     // Blue
      new Color(75, 0, 130),    // Indigo
      new Color(148, 0, 211)    // Violet
    ]],
    [Theme.LavaFlow, [
      new Color(255, 0, 0),     // Pure Red
      new Color(255, 69, 0),    // Red-Orange
      new Color(255, 140, 0),   // Dark Orange
      new Color(255, 165, 0),   // Orange
      new Color(255, 215, 0),   // Gold
      new Color(178, 34, 34),   // Firebrick
      new Color(139, 0, 0)      // Dark Red
    ]],
    // ...Add other themes as needed
  ]);

  private getCurrentThemeColors(): Color[] {
    return this.themeColors.get(this.currentTheme) || [];
  }

  // Add force field effect
  private applyForceField(particle: Particle): void {
    const fieldStrength = 50;
    const fieldFrequency = 0.01;
    const fieldPhase = Date.now() * 0.001;

    // Create a flowing force field effect
    const forceX = Math.sin(particle.y * fieldFrequency + fieldPhase) * fieldStrength;
    const forceY = Math.cos(particle.x * fieldFrequency + fieldPhase) * fieldStrength;

    particle.force.x += forceX * dt.value;
    particle.force.y += forceY * dt.value;
  }

  // Handle typing events
  public handleTyping(): void {
    const currentTime = Date.now() * 0.001;
    this.lastTypingTime = currentTime;
    this.lastTypeTime = currentTime;
    this.typeCount = Math.min(10, this.typeCount + 1);
    this.isSnowing = true;
  }

  private generateChainParticles(
    start: { x: number, y: number },
    end: { x: number, y: number },
    count: number = 5
  ): ChainParticle[] {
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    const baseColor = new Color(
      Math.random() * 0.3 + 0.6,  // Hue shifted to blue range
      0.8,                         // High saturation
      1                           // Full brightness
    );

    return Array(count).fill(null).map((_, i) => {
      const progress = i / count;
      const x = start.x + dx * progress + (Math.random() * 20 - 10);
      const y = start.y + dy * progress + (Math.random() * 20 - 10);

      return new ChainParticle(
        this.x0, this.y0,
        x, y,
        Math.random() * 2 + 4,  // size
        baseColor,
        { x: dx * 0.1, y: dy * 0.1 },  // force
        0.99,  // friction
        0.5,   // lifetime
        0.8,   // chainStrength
        Math.sqrt(dx * dx + dy * dy) / 1.5  // maxChainDistance
      );
    });
  }

  private updateParticlePools(): void {
    // Clean up dead particles and maintain particle pools
    const deadParticles = this.elements.filter(e =>
      e.isDead && e instanceof Particle
    ) as Particle[];

    if (deadParticles.length > 0) {
      this.elements = this.elements.filter(e => !e.isDead);
      // Maintain particle pool up to max size
      const poolSpaceAvailable = ParticleSystem.MAX_PARTICLES - this.particlePool.length;
      const particlesToAdd = Math.min(poolSpaceAvailable, deadParticles.length);
      if (particlesToAdd > 0) {
        deadParticles.slice(0, particlesToAdd).forEach(p => {
          p.reset();
          this.particlePool.push(p);
        });
      }
    }
  }

  public checkChainParticles(position: vscode.Position, lastPosition?: vscode.Position): void {
    if (!lastPosition) return;

    const screenPos = this.editorPositionToScreen(position);
    const lastScreenPos = this.editorPositionToScreen(lastPosition);
    const distance = Math.sqrt(
      Math.pow(screenPos.x - lastScreenPos.x, 2) +
      Math.pow(screenPos.y - lastScreenPos.y, 2)
    );

    if (distance > 50) {
      const chainCount = this.elements.filter(e => e instanceof ChainParticle).length;
      if (chainCount < ParticleSystem.MAX_CHAIN_PARTICLES) {
        const chainParticles = this.generateChainParticles(lastScreenPos, screenPos);
        this.elements.push(...chainParticles);
      }
    }
  }

  private updateParticles(): void {
    // Convert particle update logic from Kotlin 
    // Maintain same physics/behavior while adapting to TypeScript
    // Clean up dead particles and maintain particle pools
    const deadParticles = this.elements.filter(e =>
      e.isDead && e instanceof Particle
    ) as Particle[];

    if (deadParticles.length > 0) {
      this.elements = this.elements.filter(e => !e.isDead);

      // Add dead particles to pool
      deadParticles.forEach(p => this.particlePool.add(p));
    }
  }

  // Add theme color generation
  private getThemeColor(): Color {
    const colors = this.getCurrentThemeColors();
    if (colors.length === 0) {
      return new Color(
        Math.random() * 255,
        Math.random() * 255,
        Math.random() * 255
      );
    }
    return colors[Math.floor(Math.random() * colors.length)];
  }

  public handleTypingEnd(): void {
    this.isSnowing = false;
    this.typeCount = 0;
  }
}

// Settings and activation
let particleSystem: ParticleSystem | undefined;

export function activate(context: vscode.ExtensionContext) {
  console.log('Zeus Thunderbolt activating...');

  // Create particle system immediately
  particleSystem = new ParticleSystem();
  particleSystem.start();

  // Enable effects
  particleSystem.setRegularParticlesEnabled(true);
  particleSystem.setSnowEnabled(true);
  console.log('Particle system initialized');

  // Handle configuration changes
  context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('zeusThunderbolt')) {
      const config = vscode.workspace.getConfiguration('zeusThunderbolt');
      particleSystem?.setTheme(config.get('theme', 'None'));
      particleSystem?.setSnowEnabled(config.get('snowEnabled', false));
      particleSystem?.setRegularParticlesEnabled(config.get('regularParticlesEnabled', true));
      particleSystem?.setStardustParticlesEnabled(config.get('stardustParticlesEnabled', false));
      particleSystem?.setReverseParticlesEnabled(config.get('reverseParticlesEnabled', false));
      particleSystem?.setButterfliesEnabled(config.get('butterflyParticlesEnabled', false));
    }
  }));

  let lastCursorPosition: vscode.Position | undefined;

  // Track cursor movements
  context.subscriptions.push(vscode.window.onDidChangeTextEditorSelection(e => {
    if (e.textEditor === vscode.window.activeTextEditor) {
      const position = e.selections[0].active;
      particleSystem?.checkChainParticles(position, lastCursorPosition);
      particleSystem?.handleCursorMove(position);
      lastCursorPosition = position;
    }
  }));

  // Track text changes for deletion particles
  context.subscriptions.push(vscode.workspace.onDidChangeTextDocument(e => {
    if (e.contentChanges.length > 0) {
      const change = e.contentChanges[0];
      if (change.text === '' && particleSystem?.isReverseParticlesEnabled()) {
        const editor = vscode.window.activeTextEditor;
        if (editor && editor.document === e.document) {
          particleSystem.handleDeletion(change);
        }
      }
    }
  }));

  // Add typing event handler
  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument(e => {
      if (e.contentChanges.length > 0 && e.contentChanges[0].text !== '') {
        particleSystem?.handleTyping();
      }
    })
  );

  // Add typing event handler with debounce
  let typingTimeout: NodeJS.Timeout;
  context.subscriptions.push(
    vscode.workspace.onDidChangeTextDocument(e => {
      if (e.contentChanges.length > 0 && e.contentChanges[0].text !== '') {
        particleSystem?.handleTyping();

        // Debounce typing events
        clearTimeout(typingTimeout);
        typingTimeout = setTimeout(() => {
          particleSystem?.handleTypingEnd();
        }, 1000);
      }
    })
  );

  // Clean up on deactivation
  context.subscriptions.push({
    dispose: () => {
      particleSystem?.stop();
      particleSystem = undefined;
    }
  });
}

export function deactivate() { }

// Add ReverseParticle implementation
class ReverseParticle implements PhysicsElement {
  private currentSnapshotIndex: number = 0;

  constructor(
    public x0: number,
    public y0: number,
    public x: number,
    public y: number,
    private snapshots: Array<ParticleSnapshot>,
    public chainStrength: number = 0,
    public isDead: boolean = false
  ) { }

  update(_elements: PhysicsElement[]): void {
    this.currentSnapshotIndex++;
    if (this.currentSnapshotIndex >= this.snapshots.length) {
      this.isDead = true;
      return;
    }
  }

  render(ctx: CanvasRenderingContext2D): void {
    if (this.currentSnapshotIndex < 0 || this.currentSnapshotIndex >= this.snapshots.length) return;
    // Draw the current snapshot
    for (const particle of this.snapshots[this.currentSnapshotIndex].particles) {
      particle.render(ctx);
    }
  }

  reset(): void {
    this.isDead = false;
    this.currentSnapshotIndex = -1;
  }
}

// Add missing particle utility classes
class ParticleSnapshot {
  constructor(
    public particles: Particle[]
  ) { }
}

class ParticlePool {
  private pool: Particle[] = [];
  private maxSize: number;

  constructor(maxSize: number) {
    this.maxSize = maxSize;
  }

  get length(): number {
    return this.pool.length;
  }

  push(particle: Particle): void {
    if (this.pool.length < this.maxSize) {
      this.pool.push(particle);
    }
  }

  add(particle: Particle): void {
    if (this.pool.length < this.maxSize) {
      particle.reset();
      this.pool.push(particle);
    }
  }

  get(): Particle | undefined {
    return this.pool.pop();
  }

  size(): number {
    return this.pool.length;
  }
}
