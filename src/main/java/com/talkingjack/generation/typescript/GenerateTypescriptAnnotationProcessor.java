package com.talkingjack.generation.typescript;



import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

@SupportedSourceVersion( SourceVersion.RELEASE_6 )
@SupportedAnnotationTypes({})
public class GenerateTypescriptAnnotationProcessor extends AbstractProcessor {
	public static final String GENERATED_SOURCES_LOCATION = "generated.sources.location";
	public static final String GENERATED_SOURCES_LOCATION_DEFAULT = "target"+File.separator+"generated-sources"+File.separator+"pojos";
	private Types typeUtils;
	private Elements elementUtils;
//	private Filer filer;
	private Messager messager;
	private String generatedSourcesLocation;
	private File dir;
	private Map<String, String> substitutions = new HashMap<>();//direct substitutions
//	private Map<Regex, String> regex = new HashMap<>();//regex substitutions
	private Set<String> ignoreSuperclasses = new HashSet<>();
	private boolean ignorePackage = true;
	
	//hack
	private String list = "java.util.List";
	private String map = "java.util.Map";
	
	@Override
	public synchronized void init( ProcessingEnvironment env ) {
		super.init( env );
		System.out.println( getClass().getSimpleName()+".init() start");
		typeUtils = env.getTypeUtils();
		elementUtils = env.getElementUtils();
		messager = env.getMessager();
		Map<String, String> options = env.getOptions();
		System.out.println( "---- options start");
		for( Object key : options.keySet()) {
			System.out.println( key+": "+options.get(key));
		}
		System.out.println( "---- options start");

		String generatedSourcesLocation = options.get( GENERATED_SOURCES_LOCATION );
		if( generatedSourcesLocation == null ){
			String pwd = System.getProperty( "user.dir");
			generatedSourcesLocation=pwd+File.separator+GENERATED_SOURCES_LOCATION_DEFAULT;
		}
		this.generatedSourcesLocation = generatedSourcesLocation;
		try{
			dir = new File( generatedSourcesLocation );
			if( !dir.exists()){
				Files.createDirectories(dir.toPath());//TODO: maybe set the perms instead of taking defaults
			}			
		}catch( IOException ioe ){
			ioe.printStackTrace();
			throw new RuntimeException( "Problem when attempting to read / create generatedSourcesLocation.", ioe);
		}

		System.out.println( "generatedSourcesLocation: "+generatedSourcesLocation+", fullPath: "+dir.getAbsolutePath());
		substitutions.put( "java.lang.Integer", "number" );
		substitutions.put( "java.lang.Float", "number" );
		substitutions.put( "java.lang.String", "string" );
		substitutions.put( "java.lang.Boolean", "boolean" );
		substitutions.put( "org.bson.types.ObjectId", "string" );

		ignoreSuperclasses.add( "java.lang.Object");
		System.out.println( getClass().getSimpleName()+".init() end");
	}


	@Override
	public boolean process( Set<? extends TypeElement> annotations, RoundEnvironment roundEnv ){
//		System.out.println( "process( annotations: "+annotations+", roundEnv: "+roundEnv );
		System.out.println( getClass().getSimpleName()+".process() start");

		LinkedList<StringBuilder> lines = new LinkedList<StringBuilder>();
		List<String> imports = new ArrayList<String>();
		for( Element annotatedElement : roundEnv.getElementsAnnotatedWith( GenerateTypescript.class )){
			if( annotatedElement.getKind() != ElementKind.CLASS ){
				messager.printMessage( Kind.ERROR, "GenerateTypescriptAnnotationProcessor: Only classes can be annotated with"+GenerateTypescript.class.getSimpleName());
				return true;//TODO: should check annotations to ensure they are "claimed" by this processor
			}
			TypeElement typeElement = (TypeElement) annotatedElement;
			debug( typeElement );
			String lcClass = typeElement.getSimpleName().toString().toLowerCase();

			lines.add( exportClass( typeElement, imports ));
			
			for( Element child : typeElement.getEnclosedElements()){
//				TypeMirror typeMirror = child.asType();
//				messager.printMessage( Kind.MANDATORY_WARNING, "child: "+child.getSimpleName());
				switch( child.getKind()) {
					case FIELD: 
						VariableElement ve = (VariableElement) child;
						TypeMirror fieldType = ve.asType();
						String klass = fieldType.toString();
						String tsKlass = substitutions.get( klass );
						if( tsKlass == null ){
							tsKlass = listConversion( klass, imports );
						}
						if( tsKlass == null ){
							tsKlass = mapConversion( klass, imports );
						}
						if( tsKlass == null ){
							tsKlass = otherClassConversion( klass, imports );
						}
						if( tsKlass == null ){//TODO: only do conversions for java.lang classes? And java.util?
							messager.printMessage( Kind.MANDATORY_WARNING, "GenerateTypescriptAnnotationProcessor: Do not have a conversion for field type: "+klass+" found in class: "+typeElement.getSimpleName()+", current substitutions: "+substitutions );
							tsKlass = klass;
						}
						lines.add( fieldStatement( child, tsKlass ));
						break;
					default: messager.printMessage( Kind.NOTE, "GenerateTypescriptAnnotationProcessor: Do not handle kind: "+child.getKind());
				}
				
			}
			lines.add(0, blankline());
			for( String importStatement : imports ){
				lines.add( 0, new StringBuilder( importStatement ));//TODO: convert imports instead of this.
			}
			lines.add(0, blankline());
			
			lines.add( closeClass());
			lines.add( 0, generatedBy());//insert at the beginning
			
			
			File typescriptFile = new File( dir, lcClass+".ts" );
			try( PrintWriter writer = new PrintWriter( new FileWriter( typescriptFile ))){
				for( StringBuilder sb : lines ){
					writer.println( sb.toString());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			lines.clear();
			imports.clear();
		}
		System.out.println( getClass().getSimpleName()+".process() end");

		return true;//TODO: should check annotations to ensure they are "claimed" by this processor
	}
	private StringBuilder blankline(){ return new StringBuilder( "\n"); }
	private String modifiersConversion( Set<Modifier> modifiers ){//TODO: this subject is way more complex than this, but this will work for now?
		StringBuilder sb = new StringBuilder();
		for( Modifier modifier : modifiers ){
			if( modifier.name().equals( "PUBLIC" )){
				sb.append( "public");
				break;
			}
		}
		return sb.append( " ").toString();
	}
	private StringBuilder fieldStatement( Element child, String klass ){
		return new StringBuilder( "\t"+modifiersConversion( child.getModifiers())+child.getSimpleName()+": "+klass+";" );
	}
	private String otherClassConversion( String klass, List<String> imports ){
//		System.out.println( "otherClassConversion( "+klass );
		int start = klass.lastIndexOf( ".");
		if( start >= 0 ){
			String simpleClass = klass.substring( ++start );
//			System.out.println( "otherClassConversion() simpleClass: "+simpleClass );
			imports.add( "import { "+simpleClass+" } from "+"'./"+simpleClass.toLowerCase()+"';");//TODO: assumption is in ./
			return simpleClass;
		}
		return klass;
	}
	private String listConversion( String klass, List<String> imports ){
		if( klass.startsWith( list )){
			String genericType = genericTypeConversion( klass.substring( list.length()), imports );
			//HACK
			genericType = genericType.substring( 1, genericType.length() - 1 );//remove the <> not used for TypeScript arrays?
//			System.out.println( "list genericType: "+genericType+" from klass: "+klass );
			return genericType+"[]";
		}
		return null;
	}
	private String mapConversion( String klass, List<String> imports ){
		if( klass.startsWith( map )){
			String genericType = genericTypeConversion( klass.substring( map.length()), imports );
//			System.out.println( "map genericType: "+genericType+" from klass: "+klass );
			return "Map"+genericType;
		}
		return null;
	}
	private String genericTypeConversion( String genericType, List<String> imports ){
//		System.out.println( "genericTypeConversion( "+genericType );
		List<String> params = new ArrayList<>();
		int start = 0;
		for( int pos = 0; pos < genericType.length(); pos++ ){
			char current = genericType.charAt( pos );
			if( current == '<' ){
				return "<"+genericTypeConversion( genericType.substring( pos+1 ), imports )+">";
			}else if( current == '>' ){
				String last = subOrConv( genericType.substring( start, pos ), imports );
				StringBuilder sb = new StringBuilder();
				for( String param : params ){
					sb.append( param );
					sb.append( ", ");
				}
				sb.append( last );
				return sb.toString();
			}else if( current == ','){
				params.add( subOrConv( genericType.substring( start, pos ), imports ));
				start = pos + 1;
			}
		}
		return null;//Should never happen? or should return passed in value instead?
	}
	private String subOrConv( String param, List<String> imports ){
		param = param.trim();
		String sub = substitutions.get( param );
//		System.out.println( "subOrConv() found param: "+param+", sub: "+sub );
		if( sub == null ){
			param = otherClassConversion( param, imports );
		}else{
			param = sub;
		}
		return param;
	}
	private StringBuilder generatedBy(){
		return new StringBuilder( "//generated by: "+getClass().getCanonicalName()+" on "+new Date());//TODO: add github location
	}
	private StringBuilder exportClass( TypeElement typeElement, List<String> imports ) {
		return new StringBuilder( "export class "+typeElement.getSimpleName()+getInheritsClause( typeElement, imports )+" {" );
	}
	private String getInheritsClause( TypeElement typeElement, List<String> imports ){
		String superclass = typeElement.getSuperclass().toString();
		if( superclass == null || ignoreSuperclasses.contains( superclass )){//TODO: probably should ignore anything starting with java?
			return "";
		}
		if( ignorePackage ){
			int pos = superclass.lastIndexOf( ".");
			++pos;
			if( pos <= 0 ){
				return " extends "+superclass;
			}
			superclass = superclass.substring( pos );
		}
		imports.add( "import { "+superclass+" } from "+"'./"+superclass.toLowerCase()+"';");//TODO: assumption is in ./
		return " extends "+superclass;
	}
	private StringBuilder closeClass(){
		return new StringBuilder( "}");
	}
	private void debug( TypeElement te ){
		System.out.println( "--debug start: "+te.getSimpleName());
		System.out.println( "superclass: "+te.getSuperclass());
		System.out.println( "enclosingElement: "+te.getEnclosingElement());
		System.out.println( "kind: "+te.getKind());
		System.out.println( "kind: "+te.getNestingKind());
		for( TypeMirror tm : te.getInterfaces()) {
			System.out.println( "interface: "+tm.toString()+", kind: "+tm.getKind());
		}
		for( Modifier modifier : te.getModifiers()) {
			System.out.println( "modifier: "+modifier.toString()+", name: "+modifier.name()+", ordinal: "+modifier.ordinal());
		}
		for( TypeParameterElement tpe : te.getTypeParameters()) {
			System.out.println( "typeParameter: "+tpe.toString()+", kind: "+tpe.getKind());
		}
		System.out.println( "---debug end: "+te.getSimpleName());

	}
	
	@Override
	public Set<String> getSupportedAnnotationTypes(){//Notice this is for Android support per tutorial
		HashSet<String> supported = new HashSet<>();
		supported.add( GenerateTypescript.class.getCanonicalName());
		return supported; 
	}
	
	@Override
	public SourceVersion getSupportedSourceVersion(){//Notice this is for Android support per tutorial
		return SourceVersion.RELEASE_8; //or SourceVersion.latestSupported()?
	}
}
